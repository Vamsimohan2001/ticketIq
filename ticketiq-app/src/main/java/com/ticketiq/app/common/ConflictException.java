package com.ticketiq.app.common;

/** Thrown by services; {@link GlobalExceptionHandler} turns it into the matching HTTP status. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
