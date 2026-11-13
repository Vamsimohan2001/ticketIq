package com.ticketiq.core.llm;

/** A failure that retrying cannot fix, such as a bad API key or an invalid request. */
public class NonRetryableAiException extends RuntimeException {
    public NonRetryableAiException(String message) {
        super(message);
    }
}
