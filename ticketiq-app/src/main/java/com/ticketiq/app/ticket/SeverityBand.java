package com.ticketiq.app.ticket;

/** Resolution targets for business impact: A is urgent, C is routine. */
public enum SeverityBand {
    A(2), B(4), C(7);

    private final int defaultDays;

    SeverityBand(int defaultDays) {
        this.defaultDays = defaultDays;
    }

    public int defaultDays() {
        return defaultDays;
    }

    public static SeverityBand forPriority(com.ticketiq.core.model.Priority priority) {
        return switch (priority) {
            case CRITICAL, HIGH -> A;
            case MEDIUM -> B;
            case LOW -> C;
        };
    }
}
