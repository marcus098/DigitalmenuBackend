package com.modules.mainapp.payment.service;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.mainapp.payment.PaymentUrls;
import com.modules.mainapp.payment.dto.PaymentIntentResponse;
import com.modules.mainapp.payment.entity.AgencyPaymentAccountJpa;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.entity.PaymentProvider;
import com.modules.mainapp.payment.repository.PaymentRepository;
import com.modules.mainapp.payment.sumup.SumUpCheckout;
import com.modules.mainapp.payment.sumup.SumUpCredentials;
import com.modules.mainapp.payment.sumup.SumUpException;
import com.modules.mainapp.payment.sumup.SumUpGateway;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.modules.mainapp.payment.service.PaymentService.*;

/**
 * SumUp sull'account DEL LOCALE (chiave API del locale), con checkout ospitato.
 * <p>
 * SumUp NON ha la pre-autorizzazione: per gli ordini nella riserva (approvazione richiesta) il checkout viene
 * addebitato subito e il pagamento registrato come AUTHORIZED con {@code capturedUpfront = true}; all'approvazione
 * diventa COMPLETED senza chiamate a SumUp, al rifiuto/scadenza viene rimborsato per intero.
 * <p>
 * I webhook SumUp non sono firmati: lo stato si legge SEMPRE da SumUp con la chiave del locale ({@link #refresh}).
 */
@Component
public class SumUpPaymentProvider implements OnlinePaymentProvider {

    public static final String WRONG_ACCOUNT = "Rimborso da effettuare dalla dashboard SumUp dell'account originale";
    private static final int MAX_REFERENCE = 90;
    private static final String REF_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final SumUpGateway sumup;
    private final PaymentAccountService accounts;
    private final PaymentRepository paymentRepository;
    private final PaymentTransitions transitions;
    private final PaymentUrls urls;
    private final SecureRandom random = new SecureRandom();

    public SumUpPaymentProvider(SumUpGateway sumup, PaymentAccountService accounts, PaymentRepository paymentRepository,
                                PaymentTransitions transitions, PaymentUrls urls) {
        this.sumup = sumup;
        this.accounts = accounts;
        this.paymentRepository = paymentRepository;
        this.transitions = transitions;
        this.urls = urls;
    }

    @Override
    public PaymentProvider type() {
        return PaymentProvider.SUMUP;
    }

    @Override
    public boolean canReuse(PaymentJpa p, IntentRequest req) {
        if (!STATUS_PENDING.equals(p.getStatus()) || p.getSumupCheckoutId() == null) return false;
        if (Boolean.TRUE.equals(p.getApprovalRequired()) != req.approvalRequired()) return false;
        Optional<SumUpCredentials> c = accounts.sumupCredentials(req.idAgency());
        return c.isPresent() && Objects.equals(p.getSumupMerchantCode(), c.get().merchantCode());
    }

    @Override
    public PaymentIntentResponse response(PaymentJpa p, IntentRequest req) {
        return new PaymentIntentResponse.SumUp(p.getSumupCheckoutId(), p.getSumupHostedCheckoutUrl(),
                req.amountCents(), req.approvalRequired());
    }

    /** checkout_reference univoco per tentativo (SumUp rifiuta i duplicati per merchant). */
    String checkoutReference(String comandId, long attempt) {
        StringBuilder suffix = new StringBuilder("-").append(attempt).append('-');
        for (int i = 0; i < 6; i++) suffix.append(REF_CHARS.charAt(random.nextInt(REF_CHARS.length())));
        String base = comandId.length() + suffix.length() > MAX_REFERENCE
                ? comandId.substring(0, MAX_REFERENCE - suffix.length()) : comandId;
        return base + suffix;
    }

    @Override
    public PaymentIntentResponse create(IntentRequest req) {
        if (!"eur".equalsIgnoreCase(req.currency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valuta non supportata");
        }
        SumUpCredentials creds = accounts.sumupCredentials(req.idAgency())
                .orElseThrow(() -> new IllegalStateException("SumUp non configurato per il locale " + req.idAgency()));
        String token = accounts.findAccount(req.idAgency()).map(AgencyPaymentAccountJpa::getWebhookToken)
                .orElseThrow(() -> new IllegalStateException("Account pagamenti assente per il locale " + req.idAgency()));
        SumUpCheckout co;
        try {
            co = sumup.createCheckout(creds, checkoutReference(req.comandId(), req.attempt()), req.amountCents(), "EUR",
                    "Ordine " + PaymentService.shortId(req.comandId()) + " - " + req.agencyName(),
                    urls.sumupWebhookUrl(token), urls.sumupRedirectUrl(req.localname(), req.comandId()));
        } catch (SumUpException e) {
            ErrorLog.logger.error("SumUp: errore creazione checkout comanda {} locale {}: {}",
                    req.comandId(), req.idAgency(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Errore SumUp: riprova tra qualche secondo");
        }
        if (co.id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Errore SumUp: checkout non creato");
        }
        PaymentJpa payment = new PaymentJpa();
        payment.setProvider(PaymentProvider.SUMUP);
        payment.setIdAgency(req.idAgency());
        payment.setIdTable(req.idTable());
        payment.setComandId(req.comandId());
        payment.setAmountCents(req.amountCents());
        payment.setCurrency("eur");
        payment.setSumupCheckoutId(co.id());
        payment.setSumupMerchantCode(creds.merchantCode());
        payment.setSumupHostedCheckoutUrl(co.hostedCheckoutUrl());
        payment.setApprovalRequired(req.approvalRequired());
        payment.setStatus(STATUS_PENDING);
        paymentRepository.save(payment);
        return new PaymentIntentResponse.SumUp(co.id(), co.hostedCheckoutUrl(), req.amountCents(), req.approvalRequired());
    }

    /**
     * Rilegge il checkout da SumUp (con la chiave del locale) e applica la transizione corrispondente.
     *
     * @return il checkout letto
     * @throws SumUpException errore SumUp (vedi {@link SumUpException#isTransient()})
     * @throws IllegalStateException credenziali SumUp del locale assenti
     */
    public SumUpCheckout refresh(PaymentJpa p) throws SumUpException {
        SumUpCredentials creds = accounts.sumupCredentials(p.getIdAgency())
                .orElseThrow(() -> new IllegalStateException("SumUp non configurato per il locale " + p.getIdAgency()));
        SumUpCheckout co = sumup.getCheckout(creds, p.getSumupCheckoutId());
        apply(p, co);
        return co;
    }

    /** Applica lo stato del checkout (letto da SumUp, mai dal corpo del webhook) al pagamento. */
    public void apply(PaymentJpa p, SumUpCheckout co) {
        if (co.id() != null && !co.id().equals(p.getSumupCheckoutId())) {
            ErrorLog.logger.error("SumUp: checkout {} non corrisponde al pagamento {}", co.id(), p.getId());
            return;
        }
        switch (co.normalizedStatus()) {
            case SumUpCheckout.PAID -> {
                String tx = co.successfulTransactionId();
                if (tx != null && !tx.equals(p.getSumupTransactionId())) {
                    paymentRepository.setSumupTransactionId(p.getId(), tx, LocalDateTime.now());
                    p.setSumupTransactionId(tx);
                }
                Long cents = co.amount() != null ? SumUpGateway.toCents(co.amount()) : null;
                if (Boolean.TRUE.equals(p.getApprovalRequired())) {
                    transitions.onAuthorized(p, cents != null ? cents : p.getAmountCents(), true);
                } else {
                    transitions.onSucceeded(p, cents);
                }
            }
            case SumUpCheckout.FAILED -> transitions.onFailed(p);
            case SumUpCheckout.EXPIRED -> transitions.onCanceled(p);
            default -> { /* PENDING: nulla da fare */ }
        }
    }

    @Override
    public boolean cancel(PaymentJpa p) {
        if (STATUS_AUTHORIZED.equals(p.getStatus()) && Boolean.TRUE.equals(p.getCapturedUpfront())) {
            return refundUpfront(p);
        }
        if (p.getSumupCheckoutId() == null) {
            transitions.onCanceled(p);
            return true;
        }
        Optional<SumUpCredentials> creds = accounts.sumupCredentials(p.getIdAgency());
        if (creds.isEmpty() || !Objects.equals(p.getSumupMerchantCode(), creds.get().merchantCode())) {
            ErrorLog.logger.warn("SumUp: checkout {} del locale {} non gestibile (credenziali assenti o account cambiato): chiuso localmente",
                    p.getSumupCheckoutId(), p.getIdAgency());
            transitions.onCanceled(p);
            return true;
        }
        try {
            SumUpCheckout co = sumup.getCheckout(creds.get(), p.getSumupCheckoutId());
            switch (co.normalizedStatus()) {
                case SumUpCheckout.PAID -> {
                    // Pagato ma notifica non ancora arrivata: si registra il pagamento, NON annullabile
                    apply(p, co);
                    return false;
                }
                case SumUpCheckout.FAILED, SumUpCheckout.EXPIRED -> {
                    transitions.onCanceled(p);
                    return true;
                }
                default -> {
                    sumup.deactivateCheckout(creds.get(), p.getSumupCheckoutId());
                    transitions.onCanceled(p);
                    return true;
                }
            }
        } catch (SumUpException e) {
            if (e.isNotFound()) {
                transitions.onCanceled(p);
                return true;
            }
            ErrorLog.logger.warn("SumUp: impossibile annullare checkout {}: {}", p.getSumupCheckoutId(), e.getMessage());
            return false;
        }
    }

    /** Ordine rifiutato/scaduto già addebitato: rimborso totale, poi REFUNDED (+ PaymentRefundedEvent). */
    private boolean refundUpfront(PaymentJpa p) {
        long already = p.getRefundedCents() != null ? p.getRefundedCents() : 0;
        long remaining = p.getAmountCents() - already;
        try {
            if (remaining > 0) {
                SumUpCredentials creds = accounts.sumupCredentials(p.getIdAgency())
                        .orElseThrow(() -> new IllegalStateException("credenziali SumUp del locale assenti"));
                sumup.refund(creds, requireTransactionId(p, creds), remaining);
            }
            transitions.onRefunded(p, p.getAmountCents(), true);
            return true;
        } catch (SumUpException | RuntimeException e) {
            ErrorLog.logger.error("SumUp: RIMBORSO FALLITO del pagamento {} (checkout {}, comanda {}, {} cent): "
                            + "rimborsare manualmente dalla dashboard SumUp. Causa: {}",
                    p.getId(), p.getSumupCheckoutId(), p.getComandId(), remaining, e.getMessage());
            return false;
        }
    }

    private String requireTransactionId(PaymentJpa p, SumUpCredentials creds) throws SumUpException {
        if (p.getSumupTransactionId() != null) return p.getSumupTransactionId();
        SumUpCheckout co = sumup.getCheckout(creds, p.getSumupCheckoutId());
        String tx = co.successfulTransactionId();
        if (tx == null) throw new SumUpException(409, "Transazione SumUp non trovata per il checkout " + p.getSumupCheckoutId());
        paymentRepository.setSumupTransactionId(p.getId(), tx, LocalDateTime.now());
        p.setSumupTransactionId(tx);
        return tx;
    }

    /** Addebito anticipato: approvazione = COMPLETED senza chiamate a SumUp. */
    @Override
    public boolean capture(PaymentJpa p) {
        if (!Boolean.TRUE.equals(p.getCapturedUpfront())) {
            ErrorLog.logger.error("SumUp: pagamento {} AUTHORIZED senza addebito anticipato: stato inatteso", p.getId());
            return false;
        }
        if (transitions.onSucceeded(p, null)) return true;
        return p.getComandId() != null && paymentRepository.existsByComandIdAndStatus(p.getComandId(), STATUS_COMPLETED);
    }

    /** Rimborso dalla dashboard: SumUp non notifica i rimborsi, lo stato si aggiorna subito dopo la chiamata. */
    @Override
    public Map<String, Object> refund(PaymentJpa p, long amount) {
        SumUpCredentials creds = accounts.sumupCredentials(p.getIdAgency())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, WRONG_ACCOUNT));
        if (p.getSumupMerchantCode() != null && !p.getSumupMerchantCode().equals(creds.merchantCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, WRONG_ACCOUNT);
        }
        long already = p.getRefundedCents() != null ? p.getRefundedCents() : 0;
        String tx;
        try {
            tx = requireTransactionId(p, creds);
            sumup.refund(creds, tx, amount);
        } catch (SumUpException e) {
            ErrorLog.logger.error("SumUp: errore rimborso pagamento {}: {}", p.getId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Errore SumUp: " + e.getMessage());
        }
        transitions.onRefunded(p, already + amount, false);
        return Map.of("refundId", tx, "status", "succeeded", "amountCents", amount);
    }
}
