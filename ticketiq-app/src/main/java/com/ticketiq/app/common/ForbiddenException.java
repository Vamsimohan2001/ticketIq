package com.ticketiq.app.common;

/** Thrown by services; {@link GlobalExceptionHandler} turns it into the matching HTTP status. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
