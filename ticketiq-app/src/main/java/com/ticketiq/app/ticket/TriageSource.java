package com.ticketiq.app.ticket;

/** Who set the category and priority: nobody yet, the AI, or a human agent (which always wins). */
public enum TriageSource {
    NONE, AI, MANUAL
}
