package com.ticketiq.app.common;

/** Thrown by services; {@link GlobalExceptionHandler} turns it into the matching HTTP status. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
