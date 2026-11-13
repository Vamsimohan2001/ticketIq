package com.ticketiq.core.ai;

/** The model could not answer: timeout, repeated failures, or the circuit breaker is open. */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiUnavailableException(String message) {
        super(message);
    }
}
