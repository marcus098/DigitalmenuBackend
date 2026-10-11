package com.modules.translationmodule.ai;

/** Ollama disattivato, non raggiungibile o risposta non valida. */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) { super(message); }
    public AiUnavailableException(String message, Throwable cause) { super(message, cause); }
}
