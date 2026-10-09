package com.modules.mainapp.payment.service;

import com.modules.common.finders.TableUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.Comand;
import com.modules.common.model.ComandFromTable;
import com.modules.common.model.ComandFromWaiter;
import com.modules.common.model.enums.ComandStatus;
import com.modules.mainapp.payment.ComandTotalCalculator;
import com.modules.mainapp.payment.PaymentCompletedEvent;
import com.modules.mainapp.payment.dto.PaymentIntentResponse;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.repository.PaymentRepository;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    /** Obbligatoria: l'avvio fallisce se la proprietà manca (env: STRIPE_WEBHOOK_SECRET). */
    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AuthenticatedUserProvider authUserProvider;

    @Autowired
    private ComandTotalCalculator comandTotalCalculator;

    @Autowired
    private MongoComandRepository mongoComandRepository;

    @Autowired
    private TableUtils tableUtils;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @PostConstruct
    void checkWebhookSecret() {
        if (webhookSecret == null || webhookSecret.isBlank() || !webhookSecret.startsWith("whsec_")) {
            ErrorLog.logger.error("!!! STRIPE: stripe.webhook-secret mancante o placeholder (atteso 'whsec_...'). "
                    + "Il webhook /api/payments/webhook rifiuterà ogni evento e i pagamenti NON verranno confermati. !!!");
        }
    }

    /**
     * Crea (o riusa) un PaymentIntent per la comanda. L'importo è SEMPRE calcolato lato server.
     *
     * @throws ResponseStatusException 400 dati mancanti/importo non valido, 404 comanda/tavolo non del locale,
     *                                 409 comanda già pagata o eliminata
     * @throws IllegalStateException   Stripe non configurato
     */
    public PaymentIntentResponse createIntent(long idAgency, Long idTable, String comandId, String currency) {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new IllegalStateException("Stripe secret key not configured");
        }
        if (comandId == null || comandId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "comandId obbligatorio");
        }

        Comand comand = mongoComandRepository.findById(comandId)
                .filter(c -> c.getIdAgency() != null && c.getIdAgency() == idAgency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comanda non trovata"));

        Long comandTable = tableOf(comand);
        if (idTable != null && idTable > 0) {
            if (comandTable == null || !comandTable.equals(idTable)
                    || tableUtils.findByIdAndIdAgencyAndDeleted(idTable, idAgency).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comanda non trovata per questo tavolo");
            }
        }

        if (comand.getStatus() == ComandStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Comanda annullata");
        }
        if (paymentRepository.existsByComandIdAndStatus(comandId, STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Comanda già pagata");
        }

        long amountCents = comandTotalCalculator.computeTotalCents(comandId);
        if (amountCents <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Importo non valido");
        }
        String cur = currency != null && !currency.isBlank() ? currency.toLowerCase() : "eur";

        // Riusa un intent PENDING con lo stesso importo (evita intent multipli su reload della pagina)
        Optional<PaymentJpa> pending = paymentRepository
                .findFirstByComandIdAndStatusOrderByCreatedAtDesc(comandId, STATUS_PENDING)
                .filter(p -> p.getIdAgency() == idAgency && p.getAmountCents() == amountCents
                        && cur.equalsIgnoreCase(p.getCurrency()) && p.getStripeClientSecret() != null);
        if (pending.isPresent()) {
            PaymentJpa p = pending.get();
            return new PaymentIntentResponse(p.getStripeClientSecret(), p.getStripePaymentIntentId(), amountCents);
        }

        Stripe.apiKey = stripeSecretKey;

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency(cur)
                    .putMetadata("idAgency", String.valueOf(idAgency))
                    .putMetadata("comandId", comandId)
                    .putMetadata("idTable", comandTable != null ? String.valueOf(comandTable) : "")
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            PaymentJpa payment = new PaymentJpa();
            payment.setIdAgency(idAgency);
            payment.setIdTable(comandTable);
            payment.setComandId(comandId);
            payment.setAmountCents(amountCents);
            payment.setCurrency(cur);
            payment.setStripePaymentIntentId(intent.getId());
            payment.setStripeClientSecret(intent.getClientSecret());
            payment.setStatus(STATUS_PENDING);
            paymentRepository.save(payment);

            return new PaymentIntentResponse(intent.getClientSecret(), intent.getId(), amountCents);
        } catch (Exception e) {
            throw new RuntimeException("Stripe error: " + e.getMessage(), e);
        }
    }

    /**
     * Webhook Stripe. Idempotente: la transizione a COMPLETED è un UPDATE condizionale, quindi lo stesso
     * evento ricevuto due volte (retry Stripe) non ripubblica {@link PaymentCompletedEvent}.
     *
     * @throws SignatureVerificationException firma non valida (→ 400)
     */
    @Transactional
    public void handleWebhook(String payload, String sigHeader) throws SignatureVerificationException {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new SignatureVerificationException("Webhook secret not configured", sigHeader);
        }
        Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

        if ("payment_intent.succeeded".equals(event.getType())) {
            PaymentIntent intent = extractIntent(event);
            if (intent == null) return;
            int updated = paymentRepository.updateStatusIfDifferent(intent.getId(), STATUS_COMPLETED, LocalDateTime.now());
            if (updated == 0) {
                ErrorLog.logger.info("Stripe webhook: intent {} già COMPLETED o sconosciuto, ignorato", intent.getId());
                return;
            }
            paymentRepository.findByStripePaymentIntentId(intent.getId()).ifPresent(p -> {
                if (intent.getAmount() != null && intent.getAmount() != p.getAmountCents()) {
                    ErrorLog.logger.error("Stripe webhook: importo intent {} ({}) diverso da quello atteso ({})",
                            intent.getId(), intent.getAmount(), p.getAmountCents());
                }
                if (p.getComandId() != null && !p.getComandId().isBlank()) {
                    eventPublisher.publishEvent(new PaymentCompletedEvent(
                            p.getComandId(), String.valueOf(p.getIdAgency()), p.getAmountCents()));
                }
            });
        } else if ("payment_intent.payment_failed".equals(event.getType())) {
            PaymentIntent intent = extractIntent(event);
            if (intent == null) return;
            paymentRepository.findByStripePaymentIntentId(intent.getId())
                    .filter(p -> STATUS_PENDING.equals(p.getStatus()))
                    .ifPresent(p -> { p.setStatus(STATUS_FAILED); paymentRepository.save(p); });
        }
    }

    private PaymentIntent extractIntent(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject obj = deserializer.getObject().orElse(null);
        if (obj == null) {
            // Versione API dell'evento diversa da quella della libreria: deserializza comunque
            try {
                obj = deserializer.deserializeUnsafe();
            } catch (Exception e) {
                throw new IllegalStateException("Impossibile deserializzare l'evento Stripe " + event.getId(), e);
            }
        }
        return obj instanceof PaymentIntent pi ? pi : null;
    }

    private static Long tableOf(Comand comand) {
        long t = -1;
        if (comand instanceof ComandFromWaiter w) t = w.getIdTable();
        else if (comand instanceof ComandFromTable ft) t = ft.getIdTable();
        else if (comand instanceof ComandJpa j && j.getIdTable() != null) t = j.getIdTable();
        return t > 0 ? t : null;
    }

    public List<PaymentJpa> getPaymentsForAgency() {
        long idAgency = authUserProvider.getAgencyId();
        return paymentRepository.findByIdAgencyOrderByCreatedAtDesc(idAgency);
    }

    public long getTodayTotalCentsForAgency() {
        long idAgency = authUserProvider.getAgencyId();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().plusDays(1).atStartOfDay();
        return paymentRepository.findByIdAgencyAndCreatedAtBetweenAndStatus(idAgency, startOfDay, endOfDay, STATUS_COMPLETED)
                .stream().mapToLong(PaymentJpa::getAmountCents).sum();
    }
}
