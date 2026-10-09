package com.modules.mainapp.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.authmodule.model.AgencyJpa;
import com.modules.common.finders.TableUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.Comand;
import com.modules.common.model.ComandFromTable;
import com.modules.common.model.ComandFromWaiter;
import com.modules.common.model.enums.ComandStatus;
import com.modules.mainapp.payment.ComandTotalCalculator;
import com.modules.mainapp.payment.dto.PaymentIntentResponse;
import com.modules.mainapp.payment.entity.AgencyPaymentAccountJpa;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.entity.PaymentProvider;
import com.modules.mainapp.payment.repository.PaymentRepository;
import com.modules.mainapp.payment.stripe.StripeGateway;
import com.modules.mainapp.payment.sumup.SumUpCheckout;
import com.modules.mainapp.payment.sumup.SumUpException;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.ordermodule.service.ComandFlowRules;
import com.modules.ordermodule.service.ComandPaymentOperations;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pagamenti online delle comande, indipendenti dal provider: ogni locale incassa sul PROPRIO account Stripe o SumUp
 * (la piattaforma non tocca mai i soldi). Le operazioni presso il provider sono in {@link OnlinePaymentProvider},
 * le transizioni di stato (idempotenti) in {@link PaymentTransitions}.
 * <p>
 * Un pagamento resta legato al provider con cui è stato creato: cambiare provider attivo non impedisce incasso,
 * rimborso o webhook dei pagamenti già aperti.
 */
@Service
public class PaymentService implements ComandPaymentOperations {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
    /** Importo autorizzato (o, con SumUp, già addebitato) in attesa che il locale approvi l'ordine. */
    public static final String STATUS_AUTHORIZED = "AUTHORIZED";

    /** Stati di un pagamento ancora pagabile (riusabile o da annullare). */
    static final List<String> OPEN_STATUSES = List.of(STATUS_PENDING, STATUS_FAILED);
    /** Stati annullabili (incluse le autorizzazioni non ancora incassate). */
    static final List<String> CANCELABLE_STATUSES = List.of(STATUS_PENDING, STATUS_FAILED, STATUS_AUTHORIZED);

    public static final String PAYMENTS_NOT_ACTIVE = "Pagamenti online non attivi per questo locale";

    private static final ObjectMapper JSON = new ObjectMapper();

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
    private PaymentAccountService accountService;

    @Autowired
    private PaymentTransitions transitions;

    @Autowired
    private StripeGateway stripe;

    @Autowired
    private StripePaymentProvider stripeProvider;

    @Autowired
    private SumUpPaymentProvider sumupProvider;

    OnlinePaymentProvider providerFor(PaymentProvider p) {
        return p == PaymentProvider.SUMUP ? sumupProvider : stripeProvider;
    }

    OnlinePaymentProvider providerOf(PaymentJpa p) {
        return providerFor(p.getProvider());
    }

    /** Pagamenti online attivi per il locale (provider attivo con credenziali configurate). */
    public boolean isOnlinePaymentEnabled(long idAgency) {
        return accountService.isOnlinePaymentEnabled(idAgency);
    }

    /**
     * Crea (o riusa) il pagamento della comanda presso il provider attivo del locale. L'importo è SEMPRE calcolato
     * lato server.
     *
     * @param localname nome pubblico del locale (path della richiesta), usato per gli URL di ritorno
     * @throws ResponseStatusException 400 dati mancanti/importo non valido, 404 comanda/tavolo non del locale,
     *                                 409 comanda già pagata/eliminata, pagamenti non attivi o pagamento in corso
     * @throws IllegalStateException   credenziali non disponibili (→ 503)
     */
    public PaymentIntentResponse createIntent(long idAgency, String localname, Long idTable, String comandId, String currency) {
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
        if (comand.getStatus() == ComandStatus.AWAIT_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, Boolean.TRUE.equals(comand.getPaymentAuthorized())
                    ? "Pagamento già autorizzato: in attesa di conferma del locale"
                    : "Ordine in attesa di conferma del locale");
        }
        if (comand.getStatus() == ComandStatus.AWAIT_PAYMENT
                && ComandFlowRules.isPaymentExpired(comand.getCreatedAt(), LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tempo per il pagamento scaduto: ripeti l'ordine");
        }
        // Ordine nella riserva dello slot con prepagamento: va approvato dal locale prima dell'incasso definitivo
        boolean approvalRequired = comand.getStatus() == ComandStatus.AWAIT_PAYMENT
                && Boolean.TRUE.equals(comand.getApprovalRequired());
        if (Boolean.TRUE.equals(comand.getPaid()) || paymentRepository.existsByComandIdAndStatus(comandId, STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Comanda già pagata");
        }

        PaymentProvider active = accountService.enabledProvider(idAgency);
        if (active == PaymentProvider.NONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, PAYMENTS_NOT_ACTIVE);
        }
        AgencyJpa agency = accountService.requireAgency(idAgency);
        OnlinePaymentProvider provider = providerFor(active);

        long amountCents = comandTotalCalculator.computeTotalCents(comandId);
        if (amountCents <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Importo non valido");
        }
        String cur = currency != null && !currency.isBlank() ? currency.toLowerCase() : "eur";

        OnlinePaymentProvider.IntentRequest req = new OnlinePaymentProvider.IntentRequest(idAgency,
                localname != null ? localname : agency.getName(), agency.getName(), comandId, comandTable,
                amountCents, cur, approvalRequired, paymentRepository.countByComandId(comandId));

        // Riusa un pagamento aperto compatibile (evita pagamenti multipli su reload della pagina); gli altri
        // (importo cambiato, account o provider cambiato) vengono annullati presso il rispettivo provider.
        List<PaymentJpa> open = paymentRepository.findByComandIdAndStatusInOrderByCreatedAtDesc(comandId, OPEN_STATUSES);
        Optional<PaymentJpa> reusable = open.stream()
                .filter(p -> p.getIdAgency() == idAgency && p.getAmountCents() == amountCents
                        && cur.equalsIgnoreCase(p.getCurrency()) && p.getProvider() == active
                        && provider.canReuse(p, req))
                .findFirst();
        cancelStale(open.stream().filter(p -> reusable.isEmpty() || p.getId() != reusable.get().getId()).toList());

        if (reusable.isPresent()) {
            PaymentJpa p = reusable.get();
            if (!STATUS_PENDING.equals(p.getStatus())) {
                // Un intent fallito (es. carta rifiutata) resta pagabile con un altro metodo
                p.setStatus(STATUS_PENDING);
                paymentRepository.save(p);
            }
            return provider.response(p, req);
        }
        return provider.create(req);
    }

    /**
     * Annulla i pagamenti aperti non più validi. Se il provider rifiuta l'annullamento (es. appena pagato e la notifica
     * non è ancora arrivata) NON crea un nuovo pagamento: 409, così il cliente non paga due volte.
     */
    private void cancelStale(List<PaymentJpa> stale) {
        for (PaymentJpa p : stale) {
            if (!providerOf(p).cancel(p)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Un pagamento per questa comanda è in corso: riprova tra qualche secondo");
            }
        }
    }

    /** Comanda eliminata: annulla i pagamenti ancora aperti o autorizzati (best effort, errori solo loggati). */
    public void cancelOpenIntentsForComand(String comandId) {
        if (!cancelOpenIntents(comandId)) {
            ErrorLog.logger.warn("Comanda {} eliminata ma un pagamento non è annullabile/rimborsabile (forse già pagato)", comandId);
        }
    }

    /**
     * Annulla i pagamenti aperti/autorizzati della comanda (Stripe: un'autorizzazione annullata non costa
     * commissioni; SumUp: un importo già addebitato viene rimborsato per intero).
     * @return false se almeno un pagamento non è annullabile (es. appena pagato) o il rimborso è fallito
     */
    @Override
    public boolean cancelOpenIntents(String comandId) {
        if (comandId == null) return true;
        boolean all = true;
        for (PaymentJpa p : paymentRepository.findByComandIdAndStatusInOrderByCreatedAtDesc(comandId, CANCELABLE_STATUSES)) {
            try {
                if (!providerOf(p).cancel(p)) all = false;
            } catch (RuntimeException e) {
                ErrorLog.logger.error("Errore annullamento pagamento {} comanda {}", p.getId(), comandId, e);
                all = false;
            }
        }
        return all;
    }

    /**
     * Incassa l'importo autorizzato della comanda (ordine "su richiesta" approvato).
     * Stripe: capture (COMPLETED + paid arrivano dal webhook). SumUp: già addebitato, diventa subito COMPLETED.
     *
     * @return true se incassato (o già incassato), false se non c'è un'autorizzazione valida o il provider rifiuta
     */
    @Override
    public boolean captureAuthorized(String comandId) {
        if (comandId == null) return false;
        List<PaymentJpa> authorized = paymentRepository.findByComandIdAndStatusInOrderByCreatedAtDesc(
                comandId, List.of(STATUS_AUTHORIZED));
        if (authorized.isEmpty()) {
            return paymentRepository.existsByComandIdAndStatus(comandId, STATUS_COMPLETED);
        }
        PaymentJpa p = authorized.get(0);
        return providerOf(p).capture(p);
    }

    /**
     * Rimborso (totale o parziale) di un pagamento del locale del chiamante, tramite il provider del pagamento.
     */
    public Map<String, Object> refund(long paymentId, Long requestedCents) {
        long idAgency = authUserProvider.getAgencyId();
        PaymentJpa p = paymentRepository.findByIdAndIdAgency(paymentId, idAgency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pagamento non trovato"));
        if (!STATUS_COMPLETED.equals(p.getStatus()) && !STATUS_PARTIALLY_REFUNDED.equals(p.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo i pagamenti completati possono essere rimborsati");
        }
        long already = p.getRefundedCents() != null ? p.getRefundedCents() : 0;
        long remaining = p.getAmountCents() - already;
        long amount = requestedCents != null ? requestedCents : remaining;
        if (amount <= 0 || amount > remaining) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Importo rimborso non valido");
        }
        return providerOf(p).refund(p, amount);
    }

    // ── Webhook Stripe (per locale) ────────────────────────────────────────

    /**
     * Webhook Stripe dell'account del locale identificato da {@code webhookToken}, firmato con il suo webhook secret.
     * Idempotente (vedi {@link PaymentTransitions}).
     *
     * @throws SignatureVerificationException token sconosciuto, secret assente o firma non valida (→ 400)
     */
    @Transactional
    public void handleStripeWebhook(String webhookToken, String payload, String sigHeader) throws SignatureVerificationException {
        AgencyPaymentAccountJpa account = accountService.findByWebhookToken(webhookToken)
                .orElseThrow(() -> new SignatureVerificationException("Unknown webhook token", sigHeader));
        String secret = accountService.stripeWebhookSecret(account)
                .orElseThrow(() -> new SignatureVerificationException("Webhook secret not configured", sigHeader));
        Event event = stripe.constructEvent(payload, sigHeader, secret);
        processStripeEvent(account.getIdAgency(), event);
    }

    /** Visibile per i test: elabora un evento già verificato dell'account del locale idAgency. */
    void processStripeEvent(long idAgency, Event event) {
        switch (event.getType()) {
            case "payment_intent.succeeded" -> withIntent(idAgency, event, (p, pi) -> transitions.onSucceeded(p, pi.getAmount()));
            case "payment_intent.payment_failed" -> withIntent(idAgency, event, (p, pi) -> transitions.onFailed(p));
            case "payment_intent.amount_capturable_updated" -> withIntent(idAgency, event, (p, pi) -> {
                if (pi.getAmountCapturable() != null && pi.getAmountCapturable() > 0) {
                    transitions.onAuthorized(p, pi.getAmountCapturable(), false);
                }
            });
            case "payment_intent.canceled" -> withIntent(idAgency, event, (p, pi) -> transitions.onCanceled(p));
            case "charge.refunded" -> {
                if (deserialize(event) instanceof Charge charge && charge.getPaymentIntent() != null) {
                    findStripePayment(idAgency, charge.getPaymentIntent()).ifPresent(p -> transitions.onRefunded(p,
                            charge.getAmountRefunded() != null ? charge.getAmountRefunded() : 0,
                            Boolean.TRUE.equals(charge.getRefunded())));
                }
            }
            default -> { /* evento non gestito: 200 per non far ritentare Stripe */ }
        }
    }

    private interface IntentHandler {
        void handle(PaymentJpa p, PaymentIntent intent);
    }

    private void withIntent(long idAgency, Event event, IntentHandler handler) {
        if (!(deserialize(event) instanceof PaymentIntent pi) || pi.getId() == null) return;
        findStripePayment(idAgency, pi.getId()).ifPresentOrElse(p -> handler.handle(p, pi),
                () -> ErrorLog.logger.info("Stripe webhook: intent {} sconosciuto per il locale {}, ignorato", pi.getId(), idAgency));
    }

    /** Solo pagamenti del locale a cui appartiene il webhook (un account non può toccare i pagamenti di altri). */
    private Optional<PaymentJpa> findStripePayment(long idAgency, String intentId) {
        return paymentRepository.findByStripePaymentIntentId(intentId).filter(p -> p.getIdAgency() == idAgency);
    }

    static StripeObject deserialize(Event event) {
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
        return obj;
    }

    // ── SumUp: webhook e sincronizzazione ──────────────────────────────────

    /**
     * Webhook SumUp (NON firmato, corpo {event_type, id}): il corpo serve solo a sapere QUALE checkout rileggere;
     * lo stato si legge sempre da SumUp con la chiave del locale. Token/checkout sconosciuti vengono ignorati.
     *
     * @throws SumUpException errore transitorio di SumUp (→ 5xx, SumUp ritenterà)
     */
    public void handleSumUpWebhook(String webhookToken, String body) throws SumUpException {
        Optional<AgencyPaymentAccountJpa> account = accountService.findByWebhookToken(webhookToken);
        if (account.isEmpty()) {
            ErrorLog.logger.info("SumUp webhook: token sconosciuto, ignorato");
            return;
        }
        String checkoutId;
        try {
            JsonNode n = JSON.readTree(body == null ? "" : body);
            checkoutId = n != null && n.hasNonNull("id") ? n.get("id").asText() : null;
        } catch (Exception e) {
            ErrorLog.logger.info("SumUp webhook: corpo non valido, ignorato");
            return;
        }
        if (checkoutId == null || checkoutId.isBlank()) return;
        long idAgency = account.get().getIdAgency();
        Optional<PaymentJpa> payment = paymentRepository.findBySumupCheckoutId(checkoutId)
                .filter(p -> p.getIdAgency() == idAgency);
        if (payment.isEmpty()) {
            ErrorLog.logger.info("SumUp webhook: checkout {} sconosciuto per il locale {}, ignorato", checkoutId, idAgency);
            return;
        }
        try {
            sumupProvider.refresh(payment.get());
        } catch (SumUpException e) {
            if (e.isTransient()) throw e;
            ErrorLog.logger.warn("SumUp webhook: impossibile leggere il checkout {}: {}", checkoutId, e.getMessage());
        } catch (IllegalStateException e) {
            ErrorLog.logger.warn("SumUp webhook: checkout {} del locale {} non verificabile: {}", checkoutId, idAgency, e.getMessage());
        }
    }

    /**
     * Sincronizzazione richiesta dal cliente al ritorno dal checkout SumUp.
     *
     * @return {status: PENDING|PAID|FAILED|EXPIRED, comandId}
     * @throws ResponseStatusException 404 checkout non del locale, 409 SumUp non configurato, 502 errore SumUp
     */
    public Map<String, Object> syncSumUpCheckout(long idAgency, String checkoutId) {
        PaymentJpa p = paymentRepository.findBySumupCheckoutId(checkoutId)
                .filter(x -> x.getIdAgency() == idAgency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pagamento non trovato"));
        try {
            SumUpCheckout co = sumupProvider.refresh(p);
            return Map.of("status", co.normalizedStatus(), "comandId", p.getComandId() != null ? p.getComandId() : "");
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, PAYMENTS_NOT_ACTIVE);
        } catch (SumUpException e) {
            ErrorLog.logger.warn("SumUp sync: errore lettura checkout {}: {}", checkoutId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SumUp non raggiungibile: riprova tra qualche secondo");
        }
    }

    // ── Utility / dashboard ────────────────────────────────────────────────

    static String shortId(String comandId) {
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
