package com.ticketiq.core.ai;

import java.util.Optional;

/**
 * The rules that keep a human in charge (integration point IP-7). The AI only ever suggests:
 * it never closes a ticket and never sends a reply to a user. When it is unsure or broken,
 * the ticket is flagged for a person.
 */
public final class GuardrailPolicy {

    /** @param reason empty when no human is needed */
    public record Decision(boolean needsHuman, String reason) {
    }

    private final double confidenceThreshold;

    public GuardrailPolicy(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public Decision evaluate(Optional<TriageResult> triage) {
        if (triage.isEmpty()) {
            return new Decision(true, "AI triage was unavailable or returned an invalid answer");
        }
        double confidence = triage.get().confidence();
        if (confidence < confidenceThreshold) {
            return new Decision(true, String.format("Low confidence (%.2f is below %.2f)", confidence, confidenceThreshold));
        }
        return new Decision(false, "");
    }

    public double confidenceThreshold() {
        return confidenceThreshold;
    }
}
