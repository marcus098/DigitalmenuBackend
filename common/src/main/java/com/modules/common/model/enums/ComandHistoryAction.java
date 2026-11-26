package com.modules.common.model.enums;

/**
 * Tipo di voce dello storico di una comanda.
 * <ul>
 *   <li>CREATED: comanda creata (dallo staff o dal cliente);</li>
 *   <li>EDITED: righe modificate (con il dettaglio delle differenze);</li>
 *   <li>STATUS: cambio di stato (from → to);</li>
 *   <li>CHECKOUT: conto chiuso in cassa.</li>
 * </ul>
 */
public enum ComandHistoryAction {
    CREATED,
    EDITED,
    STATUS,
    CHECKOUT
}
