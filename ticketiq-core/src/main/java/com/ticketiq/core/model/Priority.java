package com.ticketiq.core.model;

/** How urgent a ticket is, lowest to highest. */
public enum Priority {
    LOW, MEDIUM, HIGH, CRITICAL;

    public static Priority parse(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toUpperCase();
        for (Priority p : values()) {
            if (p.name().equals(key)) {
                return p;
            }
        }
        return null;
    }
}
