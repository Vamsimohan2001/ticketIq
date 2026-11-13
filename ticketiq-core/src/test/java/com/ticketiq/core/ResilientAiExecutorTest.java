package com.ticketiq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticketiq.core.ai.AiUnavailableException;
import com.ticketiq.core.ai.ResilientAiExecutor;
import com.ticketiq.core.ai.ResilientAiExecutor.CircuitState;
import com.ticketiq.core.llm.NonRetryableAiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientAiExecutorTest {

    /** A clock the test can move forward by hand. */
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private ResilientAiExecutor executor(int retries, int threshold, MovableClock clock, List<Long> sleeps) {
        return new ResilientAiExecutor(Duration.ofSeconds(2), retries, Duration.ofMillis(100), threshold,
                Duration.ofSeconds(30), clock, sleeps::add);
    }

    @Test
    void returnsTheResultOnSuccess() {
        try (ResilientAiExecutor ex = executor(2, 3, new MovableClock(), new ArrayList<>())) {
            assertEquals("ok", ex.execute("test", () -> "ok"));
        }
    }

    @Test
    void retriesWithExponentialBackoffThenSucceeds() {
        List<Long> sleeps = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        try (ResilientAiExecutor ex = executor(3, 5, new MovableClock(), sleeps)) {
            String result = ex.execute("test", () -> {
                if (calls.incrementAndGet() < 3) {
                    throw new IllegalStateException("flaky");
                }
                return "finally";
            });
            assertEquals("finally", result);
            assertEquals(3, calls.get());
            assertEquals(List.of(100L, 200L), sleeps);
        }
    }

    @Test
    void givesUpAfterTheRetryBudget() {
        AtomicInteger calls = new AtomicInteger();
        try (ResilientAiExecutor ex = executor(2, 5, new MovableClock(), new ArrayList<>())) {
            assertThrows(AiUnavailableException.class, () -> ex.execute("test", () -> {
                calls.incrementAndGet();
                throw new IllegalStateException("down");
            }));
            assertEquals(3, calls.get(), "1 try + 2 retries");
        }
    }

    @Test
    void doesNotRetryWhenTheErrorIsNotRetryable() {
        AtomicInteger calls = new AtomicInteger();
        try (ResilientAiExecutor ex = executor(3, 5, new MovableClock(), new ArrayList<>())) {
            assertThrows(AiUnavailableException.class, () -> ex.execute("test", () -> {
                calls.incrementAndGet();
                throw new NonRetryableAiException("HTTP 401");
            }));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void timesOutSlowCalls() {
        try (ResilientAiExecutor ex = new ResilientAiExecutor(Duration.ofMillis(100), 0, Duration.ofMillis(1), 5,
                Duration.ofSeconds(30), Clock.systemUTC(), ms -> { })) {
            AiUnavailableException e = assertThrows(AiUnavailableException.class, () -> ex.execute("slow", () -> {
                Thread.sleep(2000);
                return "late";
            }));
            assertTrue(e.getMessage().contains("timed out"), e.getMessage());
        }
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndSkipsCalls() {
        MovableClock clock = new MovableClock();
        AtomicInteger calls = new AtomicInteger();
        try (ResilientAiExecutor ex = executor(0, 2, clock, new ArrayList<>())) {
            for (int i = 0; i < 2; i++) {
                assertThrows(AiUnavailableException.class, () -> ex.execute("t", () -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("x");
                }));
            }
            assertEquals(CircuitState.OPEN, ex.state());
            AiUnavailableException e = assertThrows(AiUnavailableException.class, () -> ex.execute("t", () -> {
                calls.incrementAndGet();
                return "never";
            }));
            assertTrue(e.getMessage().contains("circuit breaker"));
            assertEquals(2, calls.get(), "the open circuit must not call the model");
        }
    }

    @Test
    void circuitRecoversAfterTheCoolDown() {
        MovableClock clock = new MovableClock();
        try (ResilientAiExecutor ex = executor(0, 1, clock, new ArrayList<>())) {
            assertThrows(AiUnavailableException.class, () -> ex.execute("t", () -> {
                throw new IllegalStateException("x");
            }));
            assertEquals(CircuitState.OPEN, ex.state());
            clock.advance(Duration.ofSeconds(31));
            assertEquals(CircuitState.HALF_OPEN, ex.state());
            assertEquals("back", ex.execute("t", () -> "back"));
            assertEquals(CircuitState.CLOSED, ex.state());
        }
    }

    @Test
    void aFailedTrialReopensTheCircuit() {
        MovableClock clock = new MovableClock();
        try (ResilientAiExecutor ex = executor(0, 1, clock, new ArrayList<>())) {
            assertThrows(AiUnavailableException.class, () -> ex.execute("t", () -> {
                throw new IllegalStateException("x");
            }));
            clock.advance(Duration.ofSeconds(31));
            assertThrows(AiUnavailableException.class, () -> ex.execute("t", () -> {
                throw new IllegalStateException("still broken");
            }));
            assertEquals(CircuitState.OPEN, ex.state());
        }
    }
}
