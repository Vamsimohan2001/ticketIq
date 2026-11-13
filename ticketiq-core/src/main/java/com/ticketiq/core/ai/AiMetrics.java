package com.ticketiq.core.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Thread-safe counters behind GET /api/ai/stats. */
public final class AiMetrics {

    private final LongAdder calls = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder totalLatencyMs = new LongAdder();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();
    private final LongAdder triages = new LongAdder();
    private final LongAdder needsHuman = new LongAdder();
    private final LongAdder rateLimited = new LongAdder();

    public void recordCall(long latencyMs, boolean success) {
        calls.increment();
        totalLatencyMs.add(latencyMs);
        if (!success) {
            failures.increment();
        }
    }

    public void recordCache(boolean hit) {
        (hit ? cacheHits : cacheMisses).increment();
    }

    public void recordPipeline(boolean needsHumanReview) {
        triages.increment();
        if (needsHumanReview) {
            needsHuman.increment();
        }
    }

    public void recordRateLimited() {
        rateLimited.increment();
    }

    public Map<String, Object> snapshot() {
        long c = calls.sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("modelCalls", c);
        m.put("modelFailures", failures.sum());
        m.put("averageLatencyMs", c == 0 ? 0 : totalLatencyMs.sum() / c);
        m.put("cacheHits", cacheHits.sum());
        m.put("cacheMisses", cacheMisses.sum());
        m.put("pipelineRuns", triages.sum());
        m.put("sentToHuman", needsHuman.sum());
        m.put("rateLimited", rateLimited.sum());
        return m;
    }
}
