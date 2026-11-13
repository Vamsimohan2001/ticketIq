package com.ticketiq.core.ai;

import com.ticketiq.core.llm.NonRetryableAiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongConsumer;

/**
 * Runs every model call with a timeout, a few retries with exponential backoff and a simple
 * circuit breaker. Hand-rolled on purpose (no resilience library) so it is easy to read and explain.
 *
 * <p>Circuit breaker: after {@code failureThreshold} failed calls in a row the circuit OPENS and calls
 * fail instantly. After {@code openFor} one trial call is allowed (HALF_OPEN); success closes it again.
 */
public final class ResilientAiExecutor implements AutoCloseable {

    public enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    private final Duration timeout;
    private final int maxRetries;
    private final Duration baseBackoff;
    private final int failureThreshold;
    private final Duration openFor;
    private final Clock clock;
    private final LongConsumer sleeper;
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    private int consecutiveFailures;
    private Instant openedAt;
    private boolean trialInFlight;

    public ResilientAiExecutor(Duration timeout, int maxRetries) {
        this(timeout, maxRetries, Duration.ofMillis(300), 5, Duration.ofSeconds(30), Clock.systemUTC(),
                ms -> {
                    try {
                        Thread.sleep(ms);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
    }

    /** Full constructor; the clock and sleeper are injectable so tests run instantly. */
    public ResilientAiExecutor(Duration timeout, int maxRetries, Duration baseBackoff, int failureThreshold,
                               Duration openFor, Clock clock, LongConsumer sleeper) {
        this.timeout = timeout;
        this.maxRetries = Math.max(0, maxRetries);
        this.baseBackoff = baseBackoff;
        this.failureThreshold = failureThreshold;
        this.openFor = openFor;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public <T> T execute(String name, Callable<T> task) {
        acquirePermit(name);
        Throwable last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                T result = runWithTimeout(task);
                recordSuccess();
                return result;
            } catch (NonRetryableAiException e) {
                recordFailure();
                throw new AiUnavailableException(name + " failed: " + e.getMessage(), e);
            } catch (Exception e) {
                last = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                if (last instanceof NonRetryableAiException) {
                    recordFailure();
                    throw new AiUnavailableException(name + " failed: " + last.getMessage(), last);
                }
                if (attempt < maxRetries) {
                    sleeper.accept(baseBackoff.toMillis() * (1L << attempt));
                }
            }
        }
        recordFailure();
        throw new AiUnavailableException(name + " failed after " + (maxRetries + 1) + " attempt(s): "
                + (last == null ? "unknown" : last.getMessage()), last);
    }

    private <T> T runWithTimeout(Callable<T> task) throws Exception {
        Future<T> future = pool.submit(task);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("timed out after " + timeout.toSeconds() + "s");
        }
    }

    public synchronized CircuitState state() {
        if (openedAt == null) {
            return CircuitState.CLOSED;
        }
        return clock.instant().isBefore(openedAt.plus(openFor)) ? CircuitState.OPEN : CircuitState.HALF_OPEN;
    }

    private synchronized void acquirePermit(String name) {
        CircuitState s = state();
        if (s == CircuitState.OPEN) {
            throw new AiUnavailableException(name + " skipped: AI circuit breaker is open");
        }
        if (s == CircuitState.HALF_OPEN) {
            if (trialInFlight) {
                throw new AiUnavailableException(name + " skipped: AI circuit breaker is testing recovery");
            }
            trialInFlight = true;
        }
    }

    private synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openedAt = null;
        trialInFlight = false;
    }

    private synchronized void recordFailure() {
        consecutiveFailures++;
        trialInFlight = false;
        if (consecutiveFailures >= failureThreshold || openedAt != null) {
            openedAt = clock.instant();
        }
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
