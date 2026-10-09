package com.modules.mainapp.payment.service;

import com.modules.mainapp.payment.dto.PaymentIntentResponse;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.entity.PaymentProvider;

import java.util.Map;

/**
 * Operazioni presso un provider di pagamento (account del locale). Le transizioni di stato sono condivise
 * ({@link PaymentTransitions}); validazioni e scelta del provider stanno in {@link PaymentService}.
 */
public interface OnlinePaymentProvider {

    PaymentProvider type();

    /**
     * @param localname        nome pubblico del locale (dal path della richiesta), per gli URL di ritorno
     * @param approvalRequired ordine nella riserva: va approvato dal locale prima dell'incasso definitivo
     * @param attempt          numero di pagamenti già creati per la comanda (idempotenza/riferimenti univoci)
     */
    record IntentRequest(long idAgency, String localname, String agencyName, String comandId, Long idTable,
                         long amountCents, String currency, boolean approvalRequired, long attempt) {}

    /** Un pagamento aperto (PENDING/FAILED) della comanda può essere riproposto al cliente? */
    boolean canReuse(PaymentJpa open, IntentRequest req);

    /** Risposta per il cliente relativa a un pagamento esistente (riuso). */
    PaymentIntentResponse response(PaymentJpa p, IntentRequest req);

    /** Crea il pagamento presso il provider e lo registra (PENDING). */
    PaymentIntentResponse create(IntentRequest req);

    /**
     * Annulla un pagamento aperto o autorizzato (per un'autorizzazione già addebitata: rimborso totale).
     *
     * @return true se il pagamento non è più pagabile né addebitato; false se non annullabile (es. appena pagato)
     */
    boolean cancel(PaymentJpa p);

    /** Incassa un pagamento AUTHORIZED (ordine approvato). @return true se incassato o in incasso */
    boolean capture(PaymentJpa p);

    /** Rimborso richiesto dalla dashboard. @return {refundId, status, amountCents} */
    Map<String, Object> refund(PaymentJpa p, long amountCents);
}
