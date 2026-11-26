package com.modules.printmodule.model;

public enum PrintJobKind {
    NEW_ORDER,
    REPRINT,
    /** Scontrino "VARIAZIONE ORDINE": solo le differenze dopo la modifica delle righe di una comanda già in cucina. */
    VARIATION,
    TEST
}
