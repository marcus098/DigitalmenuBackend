package com.modules.ordermodule.service;

/**
 * Operazioni di pagamento richieste dal flusso ordini (implementate da PaymentService). Interfaccia per evitare
 * la dipendenza diretta ordermodule → payment (e il ciclo di bean PaymentService → totale comanda → ordini).
 */
public interface ComandPaymentOperations {

    /** Incassa l'importo autorizzato (capture manuale) della comanda. @return true se incassato (o già incassato). */
    boolean captureAuthorized(String comandId);

    /**
     * Annulla su Stripe gli intent ancora aperti/autorizzati della comanda.
     * @return true se non resta nessun intent pagabile (tutti annullati o nessuno), false se uno non è annullabile
     *         (es. appena pagato: il webhook non è ancora arrivato)
     */
    boolean cancelOpenIntents(String comandId);
}
