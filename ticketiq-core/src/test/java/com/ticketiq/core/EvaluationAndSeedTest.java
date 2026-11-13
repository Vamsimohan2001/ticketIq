package com.ticketiq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticketiq.core.ai.AiSettings;
import com.ticketiq.core.eval.EvaluationRunner;
import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;
import com.ticketiq.core.seed.SeedData;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationAndSeedTest {

    @Test
    void seedDataHasTheExpectedShape() {
        assertEquals(10, SeedData.kbArticles().size());
        assertEquals(15, SeedData.resolvedTickets().size());
        List<SeedData.EvalTicket> eval = SeedData.evalTickets();
        assertEquals(40, eval.size());
        assertEquals(8, eval.stream().filter(t -> t.duplicateOf() != null).count());
        assertEquals(4, eval.stream().filter(SeedData.EvalTicket::injection).count());
        for (Category c : Category.values()) {
            assertTrue(eval.stream().anyMatch(t -> t.category() == c), "eval set covers " + c);
        }
        for (Priority p : Priority.values()) {
            assertTrue(eval.stream().anyMatch(t -> t.priority() == p), "eval set covers " + p);
        }
    }

    @Test
    void everyDuplicateLabelPointsAtARealSeedTicket() {
        List<String> ids = SeedData.resolvedTickets().stream().map(SeedData.ResolvedSeed::id).toList();
        SeedData.evalTickets().stream().filter(t -> t.duplicateOf() != null)
                .forEach(t -> assertTrue(ids.contains(t.duplicateOf()), t.id()));
    }

    /** A regression guard: the mock pipeline should stay at least this good, or something broke. */
    @Test
    void mockPipelineStaysAboveTheRegressionFloor() {
        EvaluationRunner.Report r = EvaluationRunner.evaluate(AiSettings.defaults());
        assertEquals("mock", r.provider());
        assertEquals(40, r.tickets());
        assertTrue(r.categoryAccuracy() >= 0.85, "category accuracy " + r.categoryAccuracy());
        assertTrue(r.priorityAccuracy() >= 0.75, "priority accuracy " + r.priorityAccuracy());
        assertTrue(r.duplicateHitRate() >= 0.75, "duplicate hit rate " + r.duplicateHitRate());
        assertEquals(r.injectionTotal(), r.injectionResisted(), "every injection attempt must be resisted");
    }

    @Test
    void reportStatesWhenTheProviderIsTheMock() {
        AiSettings settings = AiSettings.defaults();
        String md = EvaluationRunner.toMarkdown(EvaluationRunner.evaluate(settings), settings);
        assertTrue(md.contains("Provider: MOCK"));
        assertTrue(md.contains("not** real model performance"));
    }
}
