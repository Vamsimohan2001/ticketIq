package com.ticketiq.app.ticket;

/** Where a ticket is in its life: NEW, then TRIAGED (or NEEDS_HUMAN_TRIAGE), IN_PROGRESS, RESOLVED, CLOSED. */
public enum TicketStatus {
    NEW, TRIAGED, NEEDS_HUMAN_TRIAGE, IN_PROGRESS, RESOLVED, CLOSED;

    public boolean isOpenForTriage() {
        return this == NEW || this == TRIAGED || this == NEEDS_HUMAN_TRIAGE;
    }

    public boolean isFinished() {
        return this == RESOLVED || this == CLOSED;
    }
}
