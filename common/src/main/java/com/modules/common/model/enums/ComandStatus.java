package com.modules.common.model.enums;

/**
 * Stati di una comanda.
 * <ul>
 *   <li>AWAIT_PAYMENT: creata ma in attesa del prepagamento online (non visibile alle dashboard, non stampata);</li>
 *   <li>AWAIT_APPROVAL: asporto nella riserva dello slot, il locale deve accettare/rifiutare (sezione "Da approvare",
 *       non stampata);</li>
 *   <li>AWAIT / PENDING: stato iniziale "normale" (tavolo / asporto);</li>
 *   <li>PROGRESS, COMPLETED, DELETED.</li>
 * </ul>
 */
public enum ComandStatus {
    AWAIT,
    PENDING,
    PROGRESS,
    DELETED,
    COMPLETED,
    AWAIT_PAYMENT,
    AWAIT_APPROVAL
}
