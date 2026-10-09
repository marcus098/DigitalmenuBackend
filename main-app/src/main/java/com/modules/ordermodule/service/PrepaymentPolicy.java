package com.modules.ordermodule.service;

/**
 * Il locale richiede il pagamento online anticipato per il canale? Implementato dal modulo pagamenti
 * (impostazione del locale AND pagamenti Stripe attivi): se Stripe viene disattivato il prepagamento decade.
 */
public interface PrepaymentPolicy {

    enum Channel { TAKEAWAY, TABLE }

    boolean requiresPrepayment(long idAgency, Channel channel);
}
