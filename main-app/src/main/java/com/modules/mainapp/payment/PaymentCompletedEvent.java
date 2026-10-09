package com.modules.mainapp.payment;

/**
 * Pubblicato (ApplicationEventPublisher) quando Stripe conferma un pagamento
 * (payment_intent.succeeded), una sola volta per PaymentIntent.
 * Ascoltato da {@link com.modules.mainapp.payment.listener.PaymentEventsListener} per marcare la comanda come pagata.
 */
public record PaymentCompletedEvent(String comandId, String idAgency, long amountCents, String paymentIntentId) {
}
