package com.modules.mainapp.payment.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.common.finders.TableUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.Comand;
import com.modules.common.model.ComandFromTable;
import com.modules.common.model.ComandFromWaiter;
import com.modules.common.model.enums.ComandStatus;
import com.modules.mainapp.payment.ApplicationFeeCalculator;
import com.modules.mainapp.payment.ComandTotalCalculator;
import com.modules.mainapp.payment.PaymentCompletedEvent;
import com.modules.mainapp.payment.dto.PaymentIntentResponse;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.repository.PaymentRepository;
import com.modules.mainapp.payment.stripe.StripeGateway;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
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
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";

    /** Stati di un intent ancora pagabile (riusabile o da annullare). */
    static final List<String> OPEN_STATUSES = List.of(STATUS_PENDING, STATUS_FAILED);

    public static final String PAYMENTS_NOT_ACTIVE = "Pagamenti online non attivi per questo locale";

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

    @Autowired
    private StripeGateway stripe;

    @Autowired
    private StripeConnectService connectService;

    @PostConstruct
    void checkWebhookSecret() {
        if (webhookSecret == null || webhookSecret.isBlank() || !webhookSecret.startsWith("whsec_")) {
            ErrorLog.logger.error("!!! STRIPE: stripe.webhook-secret mancante o placeholder (atteso 'whsec_...'). "
                    + "Il webhook /api/payments/webhook rifiuterà ogni evento e i pagamenti NON verranno confermati. !!!");
        }
    }

    /** Pagamenti online attivi per il locale (piattaforma configurata + account Connect abilitato). */
    public boolean isOnlinePaymentEnabled(long idAgency) {
        return connectService.isPaymentsEnabled(idAgency);
    }

    /**
     * Crea (o riusa) un PaymentIntent per la comanda, come destination charge verso l'account Connect del locale.
     * L'importo è SEMPRE calcolato lato server.
     *
     * @throws ResponseStatusException 400 dati mancanti/importo non valido, 404 comanda/tavolo non del locale,
     *                                 409 comanda già pagata/eliminata, pagamenti non attivi o pagamento in corso
     * @throws IllegalStateException   Stripe non configurato sulla piattaforma (→ 503)
     */
    public PaymentIntentResponse createIntent(long idAgency, Long idTable, String comandId, String currency) {
        if (!stripe.isConfigured()) {
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
        if (Boolean.TRUE.equals(comand.getPaid()) || paymentRepository.existsByComandIdAndStatus(comandId, STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Comanda già pagata");
        }

        AgencyJpa agency = connectService.requireAgency(idAgency);
        if (!StripeConnectService.canAcceptPayments(agency)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, PAYMENTS_NOT_ACTIVE);
        }
        String destination = agency.getStripeAccountId();

        long amountCents = comandTotalCalculator.computeTotalCents(comandId);
        if (amountCents <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Importo non valido");
        }
        String cur = currency != null && !currency.isBlank() ? currency.toLowerCase() : "eur";

        // Riusa un intent aperto con stesso importo/valuta/destinazione (evita intent multipli su reload della pagina);
        // gli altri intent aperti (importo cambiato, account cambiato) vengono annullati su Stripe.
        List<PaymentJpa> open = paymentRepository.findByComandIdAndStatusInOrderByCreatedAtDesc(comandId, OPEN_STATUSES);
        Optional<PaymentJpa> reusable = open.stream()
                .filter(p -> p.getIdAgency() == idAgency && p.getAmountCents() == amountCents
                        && cur.equalsIgnoreCase(p.getCurrency()) && p.getStripeClientSecret() != null
                        && destination.equals(p.getStripeAccountId()))
                .findFirst();
        cancelStale(open.stream().filter(p -> reusable.isEmpty() || p.getId() != reusable.get().getId()).toList());

        if (reusable.isPresent()) {
            PaymentJpa p = reusable.get();
            if (!STATUS_PENDING.equals(p.getStatus())) {
                // Un intent fallito (es. carta rifiutata) resta pagabile con un altro metodo
                p.setStatus(STATUS_PENDING);
                paymentRepository.save(p);
            }
            return new PaymentIntentResponse(p.getStripeClientSecret(), p.getStripePaymentIntentId(), amountCents);
        }

        long feeCents = ApplicationFeeCalculator.feeCents(amountCents, connectService.effectiveFeeBps(agency));

        PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency(cur)
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true).build())
                // Destination charge: il locale è il merchant of record (suo nome sull'estratto conto)
                .setOnBehalfOf(destination)
                .setTransferData(PaymentIntentCreateParams.TransferData.builder().setDestination(destination).build())
                .setDescription("Ordine " + shortId(comandId) + " - " + agency.getName())
                .putMetadata("idAgency", String.valueOf(idAgency))
                .putMetadata("comandId", comandId)
                .putMetadata("idTable", comandTable != null ? String.valueOf(comandTable) : "");
        if (feeCents > 0) params.setApplicationFeeAmount(feeCents);

        // Deterministica per (comanda, importo, destinazione, n. tentativi): un doppio click non crea due intent
        String idempotencyKey = "pi-" + comandId + "-" + amountCents + "-" + cur + "-" + destination
                + "-" + paymentRepository.countByComandId(comandId);

        try {
            PaymentIntent intent = stripe.createPaymentIntent(params.build(), idempotencyKey);

            Optional<PaymentJpa> already = paymentRepository.findByStripePaymentIntentId(intent.getId());
            if (already.isPresent()) {
                return new PaymentIntentResponse(intent.getClientSecret(), intent.getId(), amountCents);
            }

            PaymentJpa payment = new PaymentJpa();
            payment.setIdAgency(idAgency);
            payment.setIdTable(comandTable);
            payment.setComandId(comandId);
            payment.setAmountCents(amountCents);
            payment.setCurrency(cur);
            payment.setStripePaymentIntentId(intent.getId());
            payment.setStripeClientSecret(intent.getClientSecret());
            payment.setStripeAccountId(destination);
            payment.setApplicationFeeCents(feeCents);
            payment.setStatus(STATUS_PENDING);
            paymentRepository.save(payment);

            return new PaymentIntentResponse(intent.getClientSecret(), intent.getId(), amountCents);
        } catch (StripeException e) {
            ErrorLog.logger.error("Stripe: errore creazione PaymentIntent comanda {}", comandId, e);
            throw new RuntimeException("Stripe error: " + e.getMessage(), e);
        }
    }

    /**
     * Annulla su Stripe gli intent aperti non più validi. Se Stripe rifiuta l'annullamento (es. l'intent è appena
     * stato pagato e il webhook non è ancora arrivato) NON crea un nuovo intent: 409, così il cliente non paga due volte.
     */
    private void cancelStale(List<PaymentJpa> stale) {
        for (PaymentJpa p : stale) {
            if (!cancelIntent(p)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Un pagamento per questa comanda è in corso: riprova tra qualche secondo");
            }
        }
    }

    /** @return true se l'intent risulta annullato (o era già chiuso su Stripe senza successo) */
    private boolean cancelIntent(PaymentJpa p) {
        if (p.getStripePaymentIntentId() == null) {
            p.setStatus(STATUS_CANCELED);
            paymentRepository.save(p);
            return true;
        }
        try {
            PaymentIntent canceled = stripe.cancelPaymentIntent(p.getStripePaymentIntentId());
            if (!"canceled".equals(canceled.getStatus())) return false;
        } catch (StripeException e) {
            ErrorLog.logger.warn("Stripe: impossibile annullare intent {}: {}", p.getStripePaymentIntentId(), e.getMessage());
            return false;
        }
        paymentRepository.updateStatusIfIn(p.getStripePaymentIntentId(), OPEN_STATUSES, STATUS_CANCELED, LocalDateTime.now());
        return true;
    }

    /** Comanda eliminata: annulla gli intent ancora aperti (best effort, errori solo loggati). */
    public void cancelOpenIntentsForComand(String comandId) {
        if (comandId == null || !stripe.isConfigured()) return;
        for (PaymentJpa p : paymentRepository.findByComandIdAndStatusInOrderByCreatedAtDesc(comandId, OPEN_STATUSES)) {
            if (!cancelIntent(p)) {
                ErrorLog.logger.warn("Comanda {} eliminata ma l'intent {} non è annullabile (forse già pagato)",
                        comandId, p.getStripePaymentIntentId());
            }
        }
    }

    /**
     * Rimborso (totale o parziale) di un pagamento del locale del chiamante. Con destination charge:
     * reverse_transfer recupera i fondi dal locale e refund_application_fee restituisce la commissione piattaforma.
     * Lo stato REFUNDED/PARTIALLY_REFUNDED viene impostato dal webhook charge.refunded.
     */
    public Map<String, Object> refund(long paymentId, Long requestedCents) {
        long idAgency = authUserProvider.getAgencyId();
        PaymentJpa p = paymentRepository.findByIdAndIdAgency(paymentId, idAgency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pagamento non trovato"));
        if (!STATUS_COMPLETED.equals(p.getStatus()) && !STATUS_PARTIALLY_REFUNDED.equals(p.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo i pagamenti completati possono essere rimborsati");
        }
        if (!stripe.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe non configurato");
        }
        long already = p.getRefundedCents() != null ? p.getRefundedCents() : 0;
        long remaining = p.getAmountCents() - already;
        long amount = requestedCents != null ? requestedCents : remaining;
        if (amount <= 0 || amount > remaining) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Importo rimborso non valido");
        }
        RefundCreateParams.Builder params = RefundCreateParams.builder()
                .setPaymentIntent(p.getStripePaymentIntentId())
                .setAmount(amount)
                .putMetadata("paymentId", String.valueOf(p.getId()))
                .putMetadata("comandId", p.getComandId() != null ? p.getComandId() : "");
        if (p.getStripeAccountId() != null) {
            // Solo per destination charge (i pagamenti pre-Connect non hanno transfer né fee)
            params.setReverseTransfer(true).setRefundApplicationFee(true);
        }
        try {
            Refund refund = stripe.createRefund(params.build(), "refund-" + p.getId() + "-" + already + "-" + amount);
            return Map.of("refundId", refund.getId(), "status", String.valueOf(refund.getStatus()), "amountCents", amount);
        } catch (StripeException e) {
            ErrorLog.logger.error("Stripe: errore rimborso pagamento {}", p.getId(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Errore Stripe: " + e.getMessage());
        }
    }

    /**
     * Webhook Stripe (account piattaforma). Idempotente: ogni transizione è un UPDATE condizionale sullo stato
     * di partenza, quindi un evento ricevuto due volte (retry Stripe) o fuori ordine non ripubblica
     * {@link PaymentCompletedEvent} né riporta indietro uno stato (es. REFUNDED → COMPLETED).
     *
     * @throws SignatureVerificationException firma non valida (→ 400)
     */
    @Transactional
    public void handleWebhook(String payload, String sigHeader) throws SignatureVerificationException {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new SignatureVerificationException("Webhook secret not configured", sigHeader);
        }
        Event event = stripe.constructEvent(payload, sigHeader, webhookSecret);
        processEvent(event);
    }

    /** Visibile per i test: elabora un evento già verificato. */
    void processEvent(Event event) {
        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                PaymentIntent intent = asIntent(event);
                if (intent != null) onSucceeded(intent);
            }
            case "payment_intent.payment_failed" -> {
                PaymentIntent intent = asIntent(event);
                if (intent != null) {
                    paymentRepository.updateStatusIfIn(intent.getId(), List.of(STATUS_PENDING), STATUS_FAILED, LocalDateTime.now());
                }
            }
            case "payment_intent.canceled" -> {
                PaymentIntent intent = asIntent(event);
                if (intent != null) {
                    paymentRepository.updateStatusIfIn(intent.getId(), OPEN_STATUSES, STATUS_CANCELED, LocalDateTime.now());
                }
            }
            case "charge.refunded" -> {
                StripeObject obj = StripeConnectService.deserialize(event);
                if (obj instanceof Charge charge) onRefunded(charge);
            }
            default -> { /* evento non gestito: 200 per non far ritentare Stripe */ }
        }
    }

    private void onSucceeded(PaymentIntent intent) {
        int updated = paymentRepository.updateStatusIfIn(intent.getId(),
                List.of(STATUS_PENDING, STATUS_FAILED, STATUS_CANCELED), STATUS_COMPLETED, LocalDateTime.now());
        if (updated == 0) {
            ErrorLog.logger.info("Stripe webhook: intent {} già COMPLETED/rimborsato o sconosciuto, ignorato", intent.getId());
            return;
        }
        paymentRepository.findByStripePaymentIntentId(intent.getId()).ifPresent(p -> {
            if (intent.getAmount() != null && intent.getAmount() != p.getAmountCents()) {
                ErrorLog.logger.error("Stripe webhook: importo intent {} ({}) diverso da quello atteso ({})",
                        intent.getId(), intent.getAmount(), p.getAmountCents());
            }
            if (p.getComandId() != null && !p.getComandId().isBlank()) {
                eventPublisher.publishEvent(new PaymentCompletedEvent(
                        p.getComandId(), String.valueOf(p.getIdAgency()), p.getAmountCents(), intent.getId()));
            }
        });
    }

    private void onRefunded(Charge charge) {
        String intentId = charge.getPaymentIntent();
        if (intentId == null) return;
        paymentRepository.findByStripePaymentIntentId(intentId).ifPresent(p -> {
            long refunded = charge.getAmountRefunded() != null ? charge.getAmountRefunded() : 0;
            long previous = p.getRefundedCents() != null ? p.getRefundedCents() : 0;
            // amount_refunded è cumulativo: si tiene il massimo (eventi fuori ordine non fanno regredire)
            long total = Math.max(refunded, previous);
            p.setRefundedCents(total);
            p.setStatus(total >= p.getAmountCents() || Boolean.TRUE.equals(charge.getRefunded())
                    ? STATUS_REFUNDED : STATUS_PARTIALLY_REFUNDED);
            paymentRepository.save(p);
        });
    }

    private PaymentIntent asIntent(Event event) {
        StripeObject obj = StripeConnectService.deserialize(event);
        return obj instanceof PaymentIntent pi ? pi : null;
    }

    private static String shortId(String comandId) {
        int i = comandId.indexOf('_');
        String s = i > 0 ? comandId.substring(0, i) : comandId;
        return s.length() > 8 ? s.substring(0, 8) : s;
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
        // Incasso netto: completati + parzialmente rimborsati, al netto della quota rimborsata
        long total = 0;
        for (String status : List.of(STATUS_COMPLETED, STATUS_PARTIALLY_REFUNDED)) {
            total += paymentRepository.findByIdAgencyAndCreatedAtBetweenAndStatus(idAgency, startOfDay, endOfDay, status)
                    .stream().mapToLong(p -> p.getAmountCents() - (p.getRefundedCents() != null ? p.getRefundedCents() : 0))
                    .sum();
        }
        return total;
    }
}
