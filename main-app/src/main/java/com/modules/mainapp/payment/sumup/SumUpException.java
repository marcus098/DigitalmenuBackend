package com.modules.mainapp.payment.sumup;

/**
 * Errore delle API SumUp. httpStatus = 0 per errori di rete/timeout.
 * {@link #isTransient()}: rete, timeout, 429, 5xx (ha senso ritentare).
 */
public class SumUpException extends Exception {

    private final int httpStatus;

    public SumUpException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public SumUpException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
    }

    public int getHttpStatus() { return httpStatus; }

    public boolean isTransient() {
        return httpStatus == 0 || httpStatus == 429 || httpStatus >= 500;
    }

    public boolean isUnauthorized() {
        return httpStatus == 401 || httpStatus == 403;
    }

    public boolean isNotFound() {
        return httpStatus == 404;
    }
}
