package com.ticketiq.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticketiq.core.ai.GuardrailPolicy;
import com.ticketiq.core.ai.TriageResult;
import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GuardrailPolicyTest {

    private final GuardrailPolicy policy = new GuardrailPolicy(0.6);

    private static Optional<TriageResult> result(double confidence) {
        return Optional.of(new TriageResult(Category.BUG, Priority.HIGH, "summary", confidence, "why"));
    }

    @Test
    void confidentResultsPassWithoutAHuman() {
        assertFalse(policy.evaluate(result(0.85)).needsHuman());
    }

    @Test
    void resultExactlyAtTheThresholdIsAccepted() {
        assertFalse(policy.evaluate(result(0.6)).needsHuman());
    }

    @Test
    void lowConfidenceNeedsAHumanAndSaysWhy() {
        GuardrailPolicy.Decision d = policy.evaluate(result(0.42));
        assertTrue(d.needsHuman());
        assertTrue(d.reason().contains("0.42"), d.reason());
    }

    @Test
    void aMissingResultAlwaysNeedsAHuman() {
        GuardrailPolicy.Decision d = policy.evaluate(Optional.empty());
        assertTrue(d.needsHuman());
        assertTrue(d.reason().contains("unavailable"));
    }
}
