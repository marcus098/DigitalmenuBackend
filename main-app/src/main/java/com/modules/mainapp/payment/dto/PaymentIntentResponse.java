package com.modules.mainapp.payment.dto;

public class PaymentIntentResponse {
    private final String clientSecret;
    private final String paymentIntentId;

    public PaymentIntentResponse(String clientSecret, String paymentIntentId) {
        this.clientSecret = clientSecret;
        this.paymentIntentId = paymentIntentId;
    }

    public String getClientSecret() { return clientSecret; }
    public String getPaymentIntentId() { return paymentIntentId; }
}
