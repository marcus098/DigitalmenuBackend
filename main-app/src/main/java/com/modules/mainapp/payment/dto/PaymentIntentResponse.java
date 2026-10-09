package com.modules.mainapp.payment.dto;

public class PaymentIntentResponse {
    private final String clientSecret;
    private final String paymentIntentId;
    /** Importo calcolato lato server (centesimi): è quello effettivamente addebitato. */
    private final long amountCents;
    /** true = solo autorizzazione (capture manuale): l'importo viene addebitato solo se il locale accetta l'ordine. */
    private final boolean manualCapture;

    public PaymentIntentResponse(String clientSecret, String paymentIntentId, long amountCents) {
        this(clientSecret, paymentIntentId, amountCents, false);
    }

    public PaymentIntentResponse(String clientSecret, String paymentIntentId, long amountCents, boolean manualCapture) {
        this.clientSecret = clientSecret;
        this.paymentIntentId = paymentIntentId;
        this.amountCents = amountCents;
        this.manualCapture = manualCapture;
    }

    public String getClientSecret() { return clientSecret; }
    public String getPaymentIntentId() { return paymentIntentId; }
    public long getAmountCents() { return amountCents; }
    public boolean isManualCapture() { return manualCapture; }
}
