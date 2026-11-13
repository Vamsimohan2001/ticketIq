package com.ticketiq.core.ai;

import java.time.Instant;

/** One model call, for the audit table. Never contains ticket text. */
public record AiCallRecord(String ticketId, Kind kind, String provider, String model, long latencyMs,
                           boolean success, String error, Instant at) {

    public enum Kind { TRIAGE, EMBED, REPLY }
}
