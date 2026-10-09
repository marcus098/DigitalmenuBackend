package com.modules.mainapp.payment.dto;

public class PaymentIntentResponse {
    private final String clientSecret;
    private final String paymentIntentId;
    /** Importo calcolato lato server (centesimi): è quello effettivamente addebitato. */
    private final long amountCents;

    public PaymentIntentResponse(String clientSecret, String paymentIntentId, long amountCents) {
        this.clientSecret = clientSecret;
        this.paymentIntentId = paymentIntentId;
        this.amountCents = amountCents;
    }

    public String getClientSecret() { return clientSecret; }
    public String getPaymentIntentId() { return paymentIntentId; }
    public long getAmountCents() { return amountCents; }
}
