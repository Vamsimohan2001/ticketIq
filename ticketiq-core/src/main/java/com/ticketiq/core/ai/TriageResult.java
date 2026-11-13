package com.ticketiq.core.ai;

import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;

/** What the model decided about a ticket. Always validated before anyone uses it. */
public record TriageResult(Category category, Priority priority, String summary, double confidence, String rationale) {

    public static final int MAX_SUMMARY = 200;

    public TriageResult {
        if (category == null || priority == null) {
            throw new IllegalArgumentException("category and priority are required");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary is required");
        }
        if (summary.length() > MAX_SUMMARY) {
            throw new IllegalArgumentException("summary longer than " + MAX_SUMMARY + " characters");
        }
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        rationale = rationale == null ? "" : rationale;
    }
}
