package com.modules.mainapp.payment;

/** Pubblicato da charge.refunded. full = rimborso dell'intero importo (la comanda non risulta più pagata). */
public record PaymentRefundedEvent(String comandId, String idAgency, long refundedCents, boolean full) {
}
