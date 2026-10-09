package com.modules.ordermodule.service;

import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;

import java.time.Duration;
import java.time.LocalDateTime;

/** Regole pure (senza infrastruttura) del flusso prepagamento / approvazione. */
public final class ComandFlowRules {

    /** Tempo concesso al locale per approvare un ordine "su richiesta". */
    public static final Duration APPROVAL_WINDOW = Duration.ofMinutes(10);
    /** Tempo concesso al cliente per completare il prepagamento. */
    public static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);

    public static final String REASON_NO_ANSWER = "Nessuna risposta dal locale";
    public static final String REASON_PAYMENT_EXPIRED = "Pagamento non completato entro 15 minuti";
    public static final String REASON_CAPTURE_FAILED = "Il pagamento autorizzato non è più valido";

    private ComandFlowRules() {}

    /** Scadenza approvazione = min(inizio attesa + 10 min, inizio slot). slotStart null = solo la finestra. */
    public static LocalDateTime approvalDeadline(LocalDateTime waitingSince, LocalDateTime slotStart) {
        LocalDateTime byWindow = waitingSince.plus(APPROVAL_WINDOW);
        if (slotStart == null) return byWindow;
        return slotStart.isBefore(byWindow) ? slotStart : byWindow;
    }

    /** Stato iniziale "normale" del canale (quello che fa partire stampa e dashboard). */
    public static ComandStatus initialStatus(ComandWaiterType type) {
        return type == ComandWaiterType.TAKE_AWAY ? ComandStatus.PENDING : ComandStatus.AWAIT;
    }

    /** Stato dopo il prepagamento (capture automatica): ordini nella riserva vanno comunque in approvazione. */
    public static ComandStatus statusAfterPayment(ComandWaiterType type, Boolean approvalRequired) {
        return Boolean.TRUE.equals(approvalRequired) ? ComandStatus.AWAIT_APPROVAL : initialStatus(type);
    }

    public static boolean isPaymentExpired(LocalDateTime createdAt, LocalDateTime now) {
        return createdAt != null && !createdAt.plus(PAYMENT_WINDOW).isAfter(now);
    }

    /** Stati "nascosti" / di attesa che non si possono impostare o lasciare con il cambio stato manuale. */
    public static boolean isWaitingStatus(ComandStatus s) {
        return s == ComandStatus.AWAIT_PAYMENT || s == ComandStatus.AWAIT_APPROVAL;
    }
}
