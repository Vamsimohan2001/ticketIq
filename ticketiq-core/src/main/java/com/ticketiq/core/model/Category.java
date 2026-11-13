package com.ticketiq.core.model;

/** What a ticket is about. */
public enum Category {
    ACCESS, BILLING, BUG, HOW_TO, INTEGRATION, FEATURE_REQUEST, OTHER;

    /** Lenient parse for model output such as "how to" or "Feature-Request". */
    public static Category parse(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        for (Category c : values()) {
            if (c.name().equals(key)) {
                return c;
            }
        }
        return null;
    }
}
