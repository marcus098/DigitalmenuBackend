package com.modules.mainapp.payment;

/**
 * Pubblicato quando Stripe segnala un importo autorizzato e da incassare (payment_intent.amount_capturable_updated,
 * intent con capture_method=manual), una sola volta per PaymentIntent.
 */
public record PaymentAuthorizedEvent(String comandId, String idAgency, long amountCents, String paymentIntentId) {
}
