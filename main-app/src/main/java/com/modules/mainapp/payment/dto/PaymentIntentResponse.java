package com.modules.mainapp.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Risposta di POST /api/public/payments/intent/{localname}: la forma dipende dal provider del locale.
 * amountCents è SEMPRE l'importo calcolato lato server (quello effettivamente addebitato).
 */
public sealed interface PaymentIntentResponse permits PaymentIntentResponse.Stripe, PaymentIntentResponse.SumUp {

    String provider();

    long amountCents();

    /**
     * @param manualCapture true = solo autorizzazione: l'importo viene addebitato solo se il locale accetta l'ordine
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Stripe(String provider, String clientSecret, String paymentIntentId, long amountCents,
                  boolean manualCapture, String publishableKey) implements PaymentIntentResponse {
        public Stripe(String clientSecret, String paymentIntentId, long amountCents, boolean manualCapture,
                      String publishableKey) {
            this("STRIPE", clientSecret, paymentIntentId, amountCents, manualCapture, publishableKey);
        }
    }

    /**
     * SumUp non supporta la pre-autorizzazione: manualCapture è sempre false; con approvalRequired = true l'importo
     * è addebitato subito e rimborsato automaticamente se il locale rifiuta l'ordine.
     *
     * @param hostedCheckoutUrl pagina di pagamento SumUp; null = usare il widget SumUp con checkoutId
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record SumUp(String provider, String checkoutId, String hostedCheckoutUrl, long amountCents,
                 boolean manualCapture, boolean approvalRequired) implements PaymentIntentResponse {
        public SumUp(String checkoutId, String hostedCheckoutUrl, long amountCents, boolean approvalRequired) {
            this("SUMUP", checkoutId, hostedCheckoutUrl, amountCents, false, approvalRequired);
        }
    }
}
