package com.ticketiq.core.ai;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Sliding-window limiter: at most {@code limit} permits per key inside {@code window}. */
public final class RateLimiter {

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int limit, Duration window) {
        this(limit, window, Clock.systemUTC());
    }

    public RateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    /** Takes a permit if one is free. */
    public boolean tryAcquire(String key) {
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            long now = clock.millis();
            long cutoff = now - window.toMillis();
            while (!q.isEmpty() && q.peekFirst() <= cutoff) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    /** True when the key has used up its permits (does not consume one). */
    public boolean isBlocked(String key) {
        Deque<Long> q = hits.get(key);
        if (q == null) {
            return false;
        }
        synchronized (q) {
            long cutoff = clock.millis() - window.toMillis();
            while (!q.isEmpty() && q.peekFirst() <= cutoff) {
                q.pollFirst();
            }
            return q.size() >= limit;
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }
}
