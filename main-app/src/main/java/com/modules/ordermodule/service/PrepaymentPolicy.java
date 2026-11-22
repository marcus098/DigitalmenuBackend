package com.modules.ordermodule.service;

/**
 * Il locale richiede il pagamento online anticipato per il canale? Implementato dal modulo pagamenti
 * (impostazione del locale AND pagamenti Stripe attivi): se Stripe viene disattivato il prepagamento decade.
 */
public interface PrepaymentPolicy {

    /** DELIVERY: consegna a domicilio con modalità ONLINE_REQUIRED (vedi docs/DOMICILIO.md). */
    enum Channel { TAKEAWAY, TABLE, DELIVERY }

    boolean requiresPrepayment(long idAgency, Channel channel);

    /** Pagamento online possibile per il locale (provider attivo): serve alla scelta ONLINE della consegna. */
    default boolean onlinePaymentAvailable(long idAgency) {
        return false;
    }
}
