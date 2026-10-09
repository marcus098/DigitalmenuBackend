package com.modules.mainapp.payment.stripe;

/**
 * Credenziali Stripe (decifrate) di un locale. {@link #toString()} non espone mai la chiave.
 *
 * @param accountId account Stripe del locale (può essere null se la chiave limitata non può leggerlo)
 */
public record StripeCredentials(long idAgency, String secretKey, String accountId) {

    @Override
    public String toString() {
        return "StripeCredentials[idAgency=" + idAgency + ", accountId=" + accountId + ", secretKey=***]";
    }
}
