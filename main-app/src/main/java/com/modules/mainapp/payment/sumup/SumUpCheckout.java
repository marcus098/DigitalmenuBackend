package com.modules.mainapp.payment.sumup;

import java.math.BigDecimal;
import java.util.List;

/**
 * Checkout SumUp (GET/POST /v0.1/checkouts). status: PENDING, PAID, FAILED, EXPIRED.
 *
 * @param hostedCheckoutUrl pagina di pagamento ospitata da SumUp (può mancare: il frontend usa allora il widget)
 */
public record SumUpCheckout(String id, String checkoutReference, String status, BigDecimal amount, String currency,
                            String merchantCode, String hostedCheckoutUrl, List<Transaction> transactions) {

    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String FAILED = "FAILED";
    public static final String EXPIRED = "EXPIRED";

    public record Transaction(String id, String transactionCode, String status, BigDecimal amount) {}

    public boolean isPaid() { return PAID.equalsIgnoreCase(status); }

    /** Stato normalizzato (maiuscolo, PENDING se assente). */
    public String normalizedStatus() {
        return status == null || status.isBlank() ? PENDING : status.toUpperCase();
    }

    /** Id della transazione riuscita (necessario per il rimborso). */
    public String successfulTransactionId() {
        if (transactions == null) return null;
        String fallback = null;
        for (Transaction t : transactions) {
            if (t.id() == null) continue;
            if ("SUCCESSFUL".equalsIgnoreCase(t.status())) return t.id();
            if (fallback == null) fallback = t.id();
        }
        return fallback;
    }
}
