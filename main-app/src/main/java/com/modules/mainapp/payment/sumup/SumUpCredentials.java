package com.modules.mainapp.payment.sumup;

/** Credenziali SumUp (decifrate) di un locale. {@link #toString()} non espone mai la chiave. */
public record SumUpCredentials(long idAgency, String apiKey, String merchantCode) {

    @Override
    public String toString() {
        return "SumUpCredentials[idAgency=" + idAgency + ", merchantCode=" + merchantCode + ", apiKey=***]";
    }
}
