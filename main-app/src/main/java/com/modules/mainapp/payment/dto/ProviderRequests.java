package com.modules.mainapp.payment.dto;

/** Body delle PUT /api/payments/provider/*. */
public final class ProviderRequests {

    private ProviderRequests() {}

    /** PUT /api/payments/provider/stripe. secretKey omessa = mantieni quella salvata. */
    public record StripeKeys(String publishableKey, String secretKey, String webhookSecret) {
        @Override
        public String toString() {
            return "StripeKeys[publishableKey=" + publishableKey + ", secretKey=***, webhookSecret=***]";
        }
    }

    /** PUT /api/payments/provider/sumup. apiKey omessa = mantieni quella salvata. */
    public record SumUpKeys(String apiKey, String merchantCode) {
        @Override
        public String toString() {
            return "SumUpKeys[merchantCode=" + merchantCode + ", apiKey=***]";
        }
    }

    /** PUT /api/payments/provider/active: NONE | STRIPE | SUMUP. */
    public record Active(String provider) {}
}
