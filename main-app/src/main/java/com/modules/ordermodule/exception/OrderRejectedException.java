package com.modules.ordermodule.exception;

/**
 * Ordine rifiutato per un motivo "di business" (payload non valido, slot pieno, sessione non valida...).
 * Porta con sé lo status HTTP da restituire al client, un messaggio leggibile e, facoltativo, un codice stabile
 * (es. VERSION_CONFLICT) con cui le app distinguono i casi senza interpretare il testo.
 */
public class OrderRejectedException extends RuntimeException {
    private final int status;
    private final String code;

    public OrderRejectedException(int status, String message) {
        this(status, null, message);
    }

    public OrderRejectedException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    /** null = nessun codice (solo messaggio). */
    public String getCode() {
        return code;
    }
}
