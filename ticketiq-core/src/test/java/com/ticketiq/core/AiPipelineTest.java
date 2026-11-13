package com.ticketiq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticketiq.core.ai.AiCallRecord;
import com.ticketiq.core.ai.AiRuntime;
import com.ticketiq.core.ai.AiSettings;
import com.ticketiq.core.ai.PipelineResult;
import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;
import com.ticketiq.core.seed.SeedData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the real pipeline end to end with the offline mock models and the demo seed data. */
class AiPipelineTest {

    private AiRuntime seededRuntime() {
        AiRuntime ai = AiRuntime.create(AiSettings.defaults());
        for (SeedData.KbSeed kb : SeedData.kbArticles()) {
            ai.indexKbArticle(kb.id(), kb.title(), kb.content());
        }
        for (SeedData.ResolvedSeed r : SeedData.resolvedTickets()) {
            ai.indexResolvedTicket(r.id(), r.title(), r.description(), r.resolution(), r.category());
        }
        return ai;
    }

    @Test
    void triagesFindsDuplicatesAndDraftsAGroundedReply() {
        try (AiRuntime ai = seededRuntime()) {
            PipelineResult r = ai.pipeline().run("NEW-1", "Reset email is not arriving",
                    "I pressed Forgot password but the reset email never arrives in my inbox.");
            assertEquals(Category.ACCESS, r.category());
            assertFalse(r.needsHuman(), r.reason());
            assertTrue(r.similar().stream().anyMatch(s -> s.ticketId().equals("R-01")),
                    "should find the seeded password-reset ticket: " + r.similar());
            assertNotNull(r.suggestedReply());
            assertTrue(r.suggestedReply().contains("[kb:kb-001]") || r.suggestedReply().contains("[ticket:R-01]"),
                    r.suggestedReply());
            assertFalse(r.sources().isEmpty());
            assertEquals("mock", r.provider());
        }
    }

    @Test
    void personalDataNeverReachesTheModelOrTheStore() {
        List<String> seenByModel = new ArrayList<>();
        try (AiRuntime ai = seededRuntime()) {
            ai.gateway().addListener(rec -> seenByModel.add(rec.toString()));
            PipelineResult r = ai.pipeline().run("NEW-2", "Charged twice",
                    "I am ravi.kumar@example.com, call 9876543210, card 4111 1111 1111 1111 was charged twice.");
            assertEquals(3, r.redactions());
            assertFalse(r.summary().contains("ravi.kumar"));
            assertEquals(Category.BILLING, r.category());
            // audit records hold no ticket text at all
            assertTrue(seenByModel.stream().noneMatch(s -> s.contains("ravi.kumar") || s.contains("9876543210")));
        }
    }

    @Test
    void lowConfidenceTicketsGoToAHuman() {
        try (AiRuntime ai = seededRuntime()) {
            PipelineResult r = ai.pipeline().run("NEW-3", "Hello", "Just saying hi to the team.");
            assertTrue(r.needsHuman());
            assertTrue(r.reason().startsWith("Low confidence"), r.reason());
        }
    }

    @Test
    void noRelevantContextMeansNoSuggestedReply() {
        try (AiRuntime ai = AiRuntime.create(AiSettings.defaults())) { // empty knowledge base
            PipelineResult r = ai.pipeline().run("NEW-4", "Dark mode", "Please add a dark mode, would be nice.");
            assertNull(r.suggestedReply());
            assertTrue(r.sources().isEmpty());
            assertEquals(Category.FEATURE_REQUEST, r.category());
        }
    }

    @Test
    void identicalTicketsAreServedFromTheCache() {
        try (AiRuntime ai = seededRuntime()) {
            PipelineResult first = ai.pipeline().run("A", "Blank reports page", "Reports page is blank with a 500 error.");
            PipelineResult second = ai.pipeline().run("B", "Blank reports page", "Reports page is blank with a 500 error.");
            assertFalse(first.cached());
            assertTrue(second.cached());
            assertEquals(first.category(), second.category());
            assertEquals(1L, ai.metrics().snapshot().get("cacheHits"));
        }
    }

    @Test
    void auditRecordsAreWrittenForEveryModelCall() {
        List<AiCallRecord> records = new ArrayList<>();
        try (AiRuntime ai = seededRuntime()) {
            ai.gateway().addListener(records::add);
            ai.pipeline().run("NEW-5", "Password reset", "Cannot reset my password, email missing.");
            List<AiCallRecord.Kind> kinds = records.stream().map(AiCallRecord::kind).toList();
            assertTrue(kinds.contains(AiCallRecord.Kind.TRIAGE));
            assertTrue(kinds.contains(AiCallRecord.Kind.EMBED));
            assertTrue(kinds.contains(AiCallRecord.Kind.REPLY));
            assertTrue(records.stream().allMatch(AiCallRecord::success));
        }
    }

    @Test
    void rateLimitSendsExtraTicketsToAHumanInsteadOfCallingTheModel() {
        AiSettings s = AiSettings.defaults();
        AiSettings limited = new AiSettings(s.provider(), s.dimensions(), s.confidenceThreshold(),
                s.similarityThreshold(), s.replyContextThreshold(), s.timeoutSeconds(), s.maxRetries(), 2, 0,
                "", s.geminiBaseUrl(), s.geminiChatModel(), s.geminiEmbeddingModel(), s.ollamaBaseUrl(),
                s.ollamaChatModel(), s.ollamaEmbeddingModel());
        try (AiRuntime ai = AiRuntime.create(limited)) {
            ai.pipeline().run("1", "a", "login problem");
            ai.pipeline().run("2", "b", "billing problem");
            PipelineResult third = ai.pipeline().run("3", "c", "another problem");
            assertTrue(third.needsHuman());
            assertEquals("AI rate limit reached", third.reason());
            assertEquals(Priority.MEDIUM, third.priority());
        }
    }

    @Test
    void aBrokenModelStillProducesAUsableResult() {
        AiSettings s = new AiSettings(AiSettings.Provider.OLLAMA, 64, 0.6, 0.7, 0.5, 1, 0, 30, 0, "",
                "http://localhost:1", "x", "y", "http://127.0.0.1:1", "nope", "nope");
        try (AiRuntime ai = AiRuntime.create(s)) {
            PipelineResult r = ai.pipeline().run("X", "Printer broken", "It does not print.");
            assertTrue(r.needsHuman());
            assertEquals(Category.OTHER, r.category());
            assertEquals(0.0, r.confidence(), 0.0);
            assertEquals("Printer broken", r.summary());
        }
    }
}
