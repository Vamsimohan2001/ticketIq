package com.ticketiq.core.ai;

import com.ticketiq.core.llm.ChatModel;
import com.ticketiq.core.llm.EmbeddingModel;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The one door every model call goes through. It applies the resilience rules, measures latency,
 * updates metrics and writes the audit record, so no other class has to remember to.
 */
public final class AiGateway {

    private final ChatModel chat;
    private final EmbeddingModel embeddings;
    private final ResilientAiExecutor executor;
    private final AiMetrics metrics;
    private final List<AiCallListener> listeners = new CopyOnWriteArrayList<>();

    public AiGateway(ChatModel chat, EmbeddingModel embeddings, ResilientAiExecutor executor, AiMetrics metrics) {
        this.chat = chat;
        this.embeddings = embeddings;
        this.executor = executor;
        this.metrics = metrics;
    }

    public void addListener(AiCallListener listener) {
        listeners.add(listener);
    }

    public String chat(AiCallRecord.Kind kind, String ticketId, String system, String user, boolean jsonOnly) {
        return timed(kind, ticketId, chat.provider(), chat.model(),
                () -> executor.execute(kind.name().toLowerCase(), () -> chat.generate(system, user, jsonOnly)));
    }

    public float[] embed(String ticketId, String text) {
        return timed(AiCallRecord.Kind.EMBED, ticketId, embeddings.provider(), embeddings.model(),
                () -> executor.execute("embed", () -> embeddings.embed(text)));
    }

    public String provider() {
        return chat.provider();
    }

    public String model() {
        return chat.model();
    }

    public String embeddingModel() {
        return embeddings.model();
    }

    public ResilientAiExecutor executor() {
        return executor;
    }

    private <T> T timed(AiCallRecord.Kind kind, String ticketId, String provider, String model,
                        java.util.function.Supplier<T> call) {
        long start = System.nanoTime();
        try {
            T result = call.get();
            finish(kind, ticketId, provider, model, start, true, null);
            return result;
        } catch (RuntimeException e) {
            finish(kind, ticketId, provider, model, start, false, e.getMessage());
            throw e;
        }
    }

    private void finish(AiCallRecord.Kind kind, String ticketId, String provider, String model,
                        long startNanos, boolean ok, String error) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        metrics.recordCall(ms, ok);
        AiCallRecord record = new AiCallRecord(ticketId, kind, provider, model, ms, ok,
                error == null ? null : (error.length() > 300 ? error.substring(0, 300) : error), Instant.now());
        for (AiCallListener l : listeners) {
            try {
                l.onCall(record);
            } catch (RuntimeException ignored) {
                // an audit failure must never break triage
            }
        }
    }
}
