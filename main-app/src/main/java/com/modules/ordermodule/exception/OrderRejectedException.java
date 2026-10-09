package com.modules.ordermodule.exception;

/**
 * Ordine rifiutato per un motivo "di business" (payload non valido, slot pieno, sessione non valida...).
 * Porta con sé lo status HTTP da restituire al client e un messaggio leggibile.
 */
public class OrderRejectedException extends RuntimeException {
    private final int status;

    public OrderRejectedException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
