package com.ticketiq.app.common;

/** Thrown by services; {@link GlobalExceptionHandler} turns it into the matching HTTP status. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
