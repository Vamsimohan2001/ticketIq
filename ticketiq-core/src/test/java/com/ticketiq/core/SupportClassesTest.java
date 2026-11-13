package com.ticketiq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticketiq.core.ai.AiMetrics;
import com.ticketiq.core.ai.AiSettings;
import com.ticketiq.core.ai.RateLimiter;
import com.ticketiq.core.ai.TtlCache;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupportClassesTest {

    static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId z) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    @Test
    void cacheEntriesExpire() {
        MovableClock clock = new MovableClock();
        TtlCache<String, String> cache = new TtlCache<>(Duration.ofMinutes(1), 10, clock);
        cache.put("k", "v");
        assertEquals("v", cache.get("k").orElseThrow());
        clock.now = clock.now.plusSeconds(61);
        assertTrue(cache.get("k").isEmpty());
    }

    @Test
    void cacheNeverGrowsBeyondItsLimit() {
        TtlCache<Integer, Integer> cache = new TtlCache<>(Duration.ofHours(1), 3);
        for (int i = 0; i < 10; i++) {
            cache.put(i, i);
        }
        assertTrue(cache.size() <= 3);
    }

    @Test
    void rateLimiterAllowsALimitedNumberPerWindow() {
        MovableClock clock = new MovableClock();
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1), clock);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.isBlocked("a"));
        assertTrue(limiter.tryAcquire("b"), "keys are independent");
        clock.now = clock.now.plusSeconds(61);
        assertTrue(limiter.tryAcquire("a"), "the window slides");
    }

    @Test
    void rateLimiterCanBeReset() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1));
        limiter.tryAcquire("x");
        assertTrue(limiter.isBlocked("x"));
        limiter.reset("x");
        assertFalse(limiter.isBlocked("x"));
    }

    @Test
    void metricsSummariseCalls() {
        AiMetrics m = new AiMetrics();
        m.recordCall(100, true);
        m.recordCall(300, false);
        m.recordCache(true);
        m.recordPipeline(true);
        Map<String, Object> s = m.snapshot();
        assertEquals(2L, s.get("modelCalls"));
        assertEquals(1L, s.get("modelFailures"));
        assertEquals(200L, s.get("averageLatencyMs"));
        assertEquals(1L, s.get("cacheHits"));
        assertEquals(1L, s.get("sentToHuman"));
    }

    @Test
    void settingsReadEnvironmentWithSensibleDefaults() {
        AiSettings d = AiSettings.defaults();
        assertEquals(AiSettings.Provider.MOCK, d.provider());
        assertEquals(768, d.dimensions());
        assertEquals(0.6, d.confidenceThreshold(), 1e-9);

        AiSettings g = AiSettings.fromLookup(Map.of("AI_PROVIDER", "gemini", "GEMINI_API_KEY", "abc",
                "AI_CONFIDENCE_THRESHOLD", "0.8", "AI_TIMEOUT_SECONDS", "bad")::get);
        assertEquals(AiSettings.Provider.GEMINI, g.provider());
        assertEquals("abc", g.geminiApiKey());
        assertEquals(0.8, g.confidenceThreshold(), 1e-9);
        assertEquals(15, g.timeoutSeconds(), "unparsable numbers fall back to the default");
        assertEquals(0.75, g.similarityThreshold(), 1e-9);
    }

    @Test
    void providerParsingAcceptsProfileStyleNames() {
        assertEquals(AiSettings.Provider.OLLAMA, AiSettings.Provider.parse("ai-ollama"));
        assertEquals(AiSettings.Provider.MOCK, AiSettings.Provider.parse(""));
        boolean threw = false;
        try {
            AiSettings.Provider.parse("skynet");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assertTrue(threw);
    }
}
