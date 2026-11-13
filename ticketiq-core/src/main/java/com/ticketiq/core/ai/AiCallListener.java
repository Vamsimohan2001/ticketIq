package com.ticketiq.core.ai;

/** Hook for storing an audit row after every model call (the Spring app saves it to the database). */
@FunctionalInterface
public interface AiCallListener {
    void onCall(AiCallRecord record);
}
