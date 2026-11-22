package com.modules.mainapp.payment;

/**
 * Pubblicato quando un'autorizzazione NON ancora incassata viene annullata (dallo staff, all'eliminazione della
 * comanda o per scadenza presso il provider), una sola volta per pagamento: nessuna commissione, il cliente non
 * viene addebitato. La comanda non risulta più pagata online.
 */
public record PaymentAuthorizationVoidedEvent(String comandId, String idAgency, long amountCents, String paymentRef) {
}
