package com.ticketiq.app.common;

/** Thrown by services; {@link GlobalExceptionHandler} turns it into the matching HTTP status. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
