package com.ticketiq.app.events;

/**
 * Ticket lifecycle events are in-process. Events carry ids only; handlers reload fresh data, which
 * makes them safe to run twice.
 */
public final class TicketEvents {

    private TicketEvents() {
    }

    /** Published after a ticket is saved. The AI listens and triages it. */
    public record TicketCreated(long ticketId) {
    }

    /** Published after an agent resolves a ticket. The AI learns from it. */
    public record TicketResolved(long ticketId) {
    }

    /** Published when a knowledge-base article is created, edited or deleted. */
    public record KbChanged(long articleId, boolean deleted) {
    }
}
