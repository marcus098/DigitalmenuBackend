package com.modules.mainapp.payment.service;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.mainapp.payment.PaymentAuthorizedEvent;
import com.modules.mainapp.payment.PaymentCompletedEvent;
import com.modules.mainapp.payment.PaymentRefundedEvent;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.repository.PaymentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.modules.mainapp.payment.service.PaymentService.*;

/**
 * Transizioni di stato dei pagamenti, comuni a tutti i provider (webhook Stripe, webhook/sync SumUp, capture, ecc.).
 * Idempotenti: ogni transizione è un UPDATE condizionale sullo stato di partenza, quindi un evento ricevuto due volte
 * (retry del provider) o fuori ordine non ripubblica gli eventi di dominio né riporta indietro uno stato.
 */
@Component
public class PaymentTransitions {

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentTransitions(PaymentRepository paymentRepository, ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    private static boolean hasComand(PaymentJpa p) {
        return p.getComandId() != null && !p.getComandId().isBlank();
    }

    /** Pagamento riuscito/incassato: → COMPLETED e {@link PaymentCompletedEvent} una sola volta. */
    public boolean onSucceeded(PaymentJpa p, Long providerAmountCents) {
        int updated = paymentRepository.updateStatusIfIn(p.getId(),
                List.of(STATUS_PENDING, STATUS_FAILED, STATUS_CANCELED, STATUS_AUTHORIZED), STATUS_COMPLETED, LocalDateTime.now());
        if (updated == 0) {
            ErrorLog.logger.info("Pagamento {} ({}) già COMPLETED/rimborsato: evento ignorato", p.getId(), p.externalRef());
            return false;
        }
        if (providerAmountCents != null && providerAmountCents != p.getAmountCents()) {
            ErrorLog.logger.error("Pagamento {} ({}): importo del provider ({}) diverso da quello atteso ({})",
                    p.getId(), p.externalRef(), providerAmountCents, p.getAmountCents());
        }
        if (hasComand(p)) {
            eventPublisher.publishEvent(new PaymentCompletedEvent(
                    p.getComandId(), String.valueOf(p.getIdAgency()), p.getAmountCents(), p.externalRef()));
        }
        return true;
    }

    /**
     * Importo autorizzato in attesa di approvazione del locale: → AUTHORIZED e {@link PaymentAuthorizedEvent} una sola
     * volta (la comanda passa in "Da approvare").
     *
     * @param capturedUpfront true se l'importo è già stato addebitato (SumUp): da rimborsare se l'ordine è rifiutato
     */
    public boolean onAuthorized(PaymentJpa p, long amountCents, boolean capturedUpfront) {
        // Con addebito anticipato i soldi sono già presi: si registra anche da CANCELED, poi il listener rimborsa
        List<String> from = capturedUpfront
                ? List.of(STATUS_PENDING, STATUS_FAILED, STATUS_CANCELED)
                : List.of(STATUS_PENDING, STATUS_FAILED);
        int updated = paymentRepository.authorizeIfIn(p.getId(), from, STATUS_AUTHORIZED, capturedUpfront, LocalDateTime.now());
        if (updated == 0) {
            ErrorLog.logger.info("Pagamento {} ({}) già autorizzato/chiuso: evento ignorato", p.getId(), p.externalRef());
            return false;
        }
        if (amountCents != p.getAmountCents()) {
            ErrorLog.logger.error("Pagamento {} ({}): importo autorizzato ({}) diverso da quello atteso ({})",
                    p.getId(), p.externalRef(), amountCents, p.getAmountCents());
        }
        if (hasComand(p)) {
            eventPublisher.publishEvent(new PaymentAuthorizedEvent(
                    p.getComandId(), String.valueOf(p.getIdAgency()), amountCents, p.externalRef()));
        }
        return true;
    }

    public boolean onFailed(PaymentJpa p) {
        return paymentRepository.updateStatusIfIn(p.getId(), List.of(STATUS_PENDING), STATUS_FAILED, LocalDateTime.now()) > 0;
    }

    /** Annullato/scaduto presso il provider (mai da AUTHORIZED con addebito anticipato: va rimborsato). */
    public boolean onCanceled(PaymentJpa p) {
        List<String> from = Boolean.TRUE.equals(p.getCapturedUpfront()) ? OPEN_STATUSES : CANCELABLE_STATUSES;
        return paymentRepository.updateStatusIfIn(p.getId(), from, STATUS_CANCELED, LocalDateTime.now()) > 0;
    }

    /**
     * Rimborso (cumulativo): refunded_cents non regredisce mai (eventi fuori ordine). Pubblica
     * {@link PaymentRefundedEvent} (il listener è idempotente).
     *
     * @param totalRefundedCents totale rimborsato finora secondo il provider
     * @param providerSaysFull   il provider indica il rimborso come totale
     */
    public void onRefunded(PaymentJpa p, long totalRefundedCents, boolean providerSaysFull) {
        long previous = p.getRefundedCents() != null ? p.getRefundedCents() : 0;
        long total = Math.max(totalRefundedCents, previous);
        p.setRefundedCents(total);
        boolean full = total >= p.getAmountCents() || providerSaysFull;
        p.setStatus(full ? STATUS_REFUNDED : STATUS_PARTIALLY_REFUNDED);
        paymentRepository.save(p);
        if (hasComand(p)) {
            eventPublisher.publishEvent(new PaymentRefundedEvent(
                    p.getComandId(), String.valueOf(p.getIdAgency()), total, full));
        }
    }
}
