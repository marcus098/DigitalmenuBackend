package com.modules.mainapp.payment.service;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.mainapp.payment.dto.PaymentIntentResponse;
import com.modules.mainapp.payment.entity.AgencyPaymentAccountJpa;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.entity.PaymentProvider;
import com.modules.mainapp.payment.repository.PaymentRepository;
import com.modules.mainapp.payment.stripe.StripeCredentials;
import com.modules.mainapp.payment.stripe.StripeGateway;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.modules.mainapp.payment.service.PaymentService.STATUS_PENDING;

/**
 * Stripe sull'account DEL LOCALE (chiavi del locale): addebito diretto, nessun on_behalf_of / transfer_data /
 * application fee. Ordini nella riserva: capture manuale (autorizzazione, incasso all'approvazione).
 */
@Component
public class StripePaymentProvider implements OnlinePaymentProvider {

    public static final String WRONG_ACCOUNT = "Rimborso da effettuare dalla dashboard Stripe dell'account originale";

    private final StripeGateway stripe;
    private final PaymentAccountService accounts;
    private final PaymentRepository paymentRepository;
    private final PaymentTransitions transitions;

    public StripePaymentProvider(StripeGateway stripe, PaymentAccountService accounts,
                                 PaymentRepository paymentRepository, PaymentTransitions transitions) {
        this.stripe = stripe;
        this.accounts = accounts;
        this.paymentRepository = paymentRepository;
        this.transitions = transitions;
    }

    @Override
    public PaymentProvider type() {
        return PaymentProvider.STRIPE;
    }

    /**
     * Riferimento dell'account su cui è stato creato un intent: id account se noto, altrimenti l'impronta della
     * chiave (chiave limitata senza lettura account). Cambiare account => niente riuso né idempotency key condivise.
     */
    static String accountRef(StripeCredentials c) {
        return c.accountId() != null ? c.accountId() : "key:" + StripeGateway.fingerprint(c.secretKey());
    }

    /** true se il pagamento appartiene sicuramente a un altro account Stripe rispetto a quello attuale. */
    static boolean otherAccount(PaymentJpa p, StripeCredentials c) {
        String ref = p.getStripeAccountId();
        if (ref == null) return false;
        String current = accountRef(c);
        if (ref.equals(current)) return false;
        // Confronto affidabile solo tra id account; impronte diverse possono essere chiavi diverse dello stesso account
        return ref.startsWith("acct_") && current.startsWith("acct_");
    }

    private StripeCredentials requireCredentials(long idAgency) {
        return accounts.stripeCredentials(idAgency)
                .orElseThrow(() -> new IllegalStateException("Stripe non configurato per il locale " + idAgency));
    }

    private String publishableKey(long idAgency) {
        return accounts.findAccount(idAgency).map(AgencyPaymentAccountJpa::getStripePublishableKey).orElse(null);
    }

    @Override
    public boolean canReuse(PaymentJpa p, IntentRequest req) {
        if (p.getStripeClientSecret() == null || p.getStripePaymentIntentId() == null) return false;
        Optional<StripeCredentials> c = accounts.stripeCredentials(req.idAgency());
        return c.isPresent() && Objects.equals(p.getStripeAccountId(), accountRef(c.get()));
    }

    @Override
    public PaymentIntentResponse response(PaymentJpa p, IntentRequest req) {
        return new PaymentIntentResponse.Stripe(p.getStripeClientSecret(), p.getStripePaymentIntentId(),
                req.amountCents(), req.approvalRequired(), publishableKey(req.idAgency()));
    }

    /** Parametri dell'intent: addebito sull'account del locale, SENZA destination/fee. Visibile per i test. */
    static PaymentIntentCreateParams buildParams(IntentRequest req) {
        PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                .setAmount(req.amountCents())
                .setCurrency(req.currency())
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true).build())
                .setDescription("Ordine " + PaymentService.shortId(req.comandId()) + " - " + req.agencyName())
                .putMetadata("idAgency", String.valueOf(req.idAgency()))
                .putMetadata("comandId", req.comandId())
                .putMetadata("idTable", req.idTable() != null ? String.valueOf(req.idTable()) : "");
        if (req.approvalRequired()) {
            // Ordine nella riserva: si autorizza soltanto, l'incasso avviene all'approvazione del locale
            params.setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                    .putMetadata("approvalRequired", "true");
        }
        return params.build();
    }

    @Override
    public PaymentIntentResponse create(IntentRequest req) {
        StripeCredentials creds = requireCredentials(req.idAgency());
        String ref = accountRef(creds);
        // Deterministica per (comanda, importo, account, modalità, n. tentativi): un doppio click non crea due intent
        String idempotencyKey = "pi-" + req.comandId() + "-" + req.amountCents() + "-" + req.currency() + "-" + ref
                + (req.approvalRequired() ? "-manual" : "") + "-" + req.attempt();
        try {
            PaymentIntent intent = stripe.createPaymentIntent(creds, buildParams(req), idempotencyKey);
            PaymentIntentResponse resp = new PaymentIntentResponse.Stripe(intent.getClientSecret(), intent.getId(),
                    req.amountCents(), req.approvalRequired(), publishableKey(req.idAgency()));
            if (paymentRepository.findByStripePaymentIntentId(intent.getId()).isPresent()) return resp;

            PaymentJpa payment = new PaymentJpa();
            payment.setProvider(PaymentProvider.STRIPE);
            payment.setIdAgency(req.idAgency());
            payment.setIdTable(req.idTable());
            payment.setComandId(req.comandId());
            payment.setAmountCents(req.amountCents());
            payment.setCurrency(req.currency());
            payment.setStripePaymentIntentId(intent.getId());
            payment.setStripeClientSecret(intent.getClientSecret());
            payment.setStripeAccountId(ref);
            payment.setApprovalRequired(req.approvalRequired());
            payment.setStatus(STATUS_PENDING);
            paymentRepository.save(payment);
            return resp;
        } catch (StripeException e) {
            ErrorLog.logger.error("Stripe: errore creazione PaymentIntent comanda {} locale {}: {}",
                    req.comandId(), req.idAgency(), e.getMessage());
            throw new RuntimeException("Stripe error: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean cancel(PaymentJpa p) {
        if (p.getStripePaymentIntentId() == null) {
            transitions.onCanceled(p);
            return true;
        }
        Optional<StripeCredentials> creds = accounts.stripeCredentials(p.getIdAgency());
        if (creds.isEmpty() || otherAccount(p, creds.get())) {
            // Credenziali rimosse o account cambiato: l'intent non è più gestibile da qui (un'autorizzazione non
            // incassata scade da sola su Stripe dopo 7 giorni). Si chiude localmente per non bloccare le scadenze.
            ErrorLog.logger.warn("Stripe: intent {} del locale {} non annullabile (credenziali assenti o account cambiato): chiuso localmente",
                    p.getStripePaymentIntentId(), p.getIdAgency());
            transitions.onCanceled(p);
            return true;
        }
        try {
            PaymentIntent canceled = stripe.cancelPaymentIntent(creds.get(), p.getStripePaymentIntentId());
            if (!"canceled".equals(canceled.getStatus())) return false;
        } catch (StripeException e) {
            ErrorLog.logger.warn("Stripe: impossibile annullare intent {}: {}", p.getStripePaymentIntentId(), e.getMessage());
            return false;
        }
        transitions.onCanceled(p);
        return true;
    }

    /** Incasso con idempotency key per intent (un doppio click non incassa due volte); COMPLETED arriva dal webhook. */
    @Override
    public boolean capture(PaymentJpa p) {
        Optional<StripeCredentials> creds = accounts.stripeCredentials(p.getIdAgency());
        if (creds.isEmpty()) {
            ErrorLog.logger.error("Stripe: impossibile incassare intent {}: credenziali del locale {} assenti",
                    p.getStripePaymentIntentId(), p.getIdAgency());
            return false;
        }
        try {
            PaymentIntent captured = stripe.capturePaymentIntent(creds.get(), p.getStripePaymentIntentId(),
                    "capture-" + p.getStripePaymentIntentId());
            boolean ok = "succeeded".equals(captured.getStatus()) || "processing".equals(captured.getStatus());
            if (!ok) {
                ErrorLog.logger.error("Stripe: capture intent {} in stato inatteso {}", p.getStripePaymentIntentId(), captured.getStatus());
            }
            return ok;
        } catch (StripeException e) {
            ErrorLog.logger.error("Stripe: errore capture intent {} comanda {}: {}",
                    p.getStripePaymentIntentId(), p.getComandId(), e.getMessage());
            return false;
        }
    }

    /** Rimborso semplice sull'account del locale. REFUNDED/PARTIALLY_REFUNDED arrivano dal webhook charge.refunded. */
    @Override
    public Map<String, Object> refund(PaymentJpa p, long amount) {
        StripeCredentials creds = accounts.stripeCredentials(p.getIdAgency())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, WRONG_ACCOUNT));
        if (otherAccount(p, creds)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, WRONG_ACCOUNT);
        }
        long already = p.getRefundedCents() != null ? p.getRefundedCents() : 0;
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(p.getStripePaymentIntentId())
                .setAmount(amount)
                .putMetadata("paymentId", String.valueOf(p.getId()))
                .putMetadata("comandId", p.getComandId() != null ? p.getComandId() : "")
                .build();
        try {
            Refund refund = stripe.createRefund(creds, params, "refund-" + p.getId() + "-" + already + "-" + amount);
            return Map.of("refundId", refund.getId(), "status", String.valueOf(refund.getStatus()), "amountCents", amount);
        } catch (StripeException e) {
            ErrorLog.logger.error("Stripe: errore rimborso pagamento {}: {}", p.getId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Errore Stripe: " + e.getMessage());
        }
    }
}
