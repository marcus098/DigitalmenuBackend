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

    /** Esito del controllo dei pagamenti online prima di modificare le righe della comanda. */
    enum ItemsEditGuard {
        /** Nessun pagamento online valido: si può modificare (gli intent aperti sul vecchio totale sono stati annullati). */
        NONE,
        /** Importo autorizzato e non ancora incassato. */
        AUTHORIZED,
        /** Importo già incassato (anche se in parte rimborsato). */
        CAPTURED,
        /** Un pagamento aperto non è annullabile (forse appena pagato): riprovare tra poco. */
        BUSY
    }

    /**
     * Prima di cambiare le righe (e quindi il totale): segnala autorizzazioni/incassi esistenti e annulla gli intent
     * ancora aperti (PENDING/FAILED), calcolati sul vecchio totale, così il cliente non paga l'importo sbagliato.
     */
    ItemsEditGuard prepareForItemsEdit(String comandId);
}
