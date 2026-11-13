package com.ticketiq.core.ai;

import com.ticketiq.core.llm.ChatModel;
import com.ticketiq.core.llm.EmbeddingModel;
import com.ticketiq.core.llm.GeminiChatModel;
import com.ticketiq.core.llm.GeminiEmbeddingModel;
import com.ticketiq.core.llm.HashingEmbeddingModel;
import com.ticketiq.core.llm.MockChatModel;
import com.ticketiq.core.llm.OllamaChatModel;
import com.ticketiq.core.llm.OllamaEmbeddingModel;
import com.ticketiq.core.model.Category;
import com.ticketiq.core.redaction.PiiRedactor;
import java.time.Duration;

/**
 * Builds and holds the whole AI stack for the chosen provider. This is the single entry point the
 * Spring app (or the evaluation runner) uses, so provider switching happens in exactly one place.
 *
 * <p>Also owns the learning loop (IP-8): resolved tickets and knowledge-base articles are embedded
 * and added to the vector store so future tickets can learn from them.
 */
public final class AiRuntime implements AutoCloseable {

    private final AiSettings settings;
    private final AiGateway gateway;
    private final VectorStore store;
    private final AiMetrics metrics;
    private final AiPipeline pipeline;
    private final PiiRedactor redactor;

    private AiRuntime(AiSettings settings, ChatModel chat, EmbeddingModel embeddings) {
        this.settings = settings;
        this.metrics = new AiMetrics();
        this.redactor = new PiiRedactor();
        this.store = new InMemoryVectorStore();
        Duration timeout = Duration.ofSeconds(settings.timeoutSeconds());
        ResilientAiExecutor executor = new ResilientAiExecutor(timeout, settings.maxRetries());
        this.gateway = new AiGateway(chat, embeddings, executor, metrics);
        TriageService triage = new TriageService(gateway);
        SimilarityService similarity = new SimilarityService(store, settings.similarityThreshold());
        ReplyDraftService replies = new ReplyDraftService(gateway, store, settings.replyContextThreshold());
        GuardrailPolicy guardrails = new GuardrailPolicy(settings.confidenceThreshold());
        this.pipeline = new AiPipeline(redactor, triage, similarity, replies, guardrails, gateway, metrics,
                settings.rateLimitPerMinute(), Duration.ofSeconds(settings.cacheTtlSeconds()));
    }

    public static AiRuntime create(AiSettings s) {
        Duration timeout = Duration.ofSeconds(s.timeoutSeconds());
        return switch (s.provider()) {
            case MOCK -> new AiRuntime(s, new MockChatModel(), new HashingEmbeddingModel(s.dimensions()));
            case GEMINI -> new AiRuntime(s,
                    new GeminiChatModel(s.geminiBaseUrl(), s.geminiApiKey(), s.geminiChatModel(), timeout),
                    new GeminiEmbeddingModel(s.geminiBaseUrl(), s.geminiApiKey(), s.geminiEmbeddingModel(),
                            s.dimensions(), timeout));
            case OLLAMA -> new AiRuntime(s,
                    new OllamaChatModel(s.ollamaBaseUrl(), s.ollamaChatModel(), timeout),
                    new OllamaEmbeddingModel(s.ollamaBaseUrl(), s.ollamaEmbeddingModel(), s.dimensions(), timeout));
        };
    }

    public AiPipeline pipeline() {
        return pipeline;
    }

    public AiGateway gateway() {
        return gateway;
    }

    public VectorStore vectorStore() {
        return store;
    }

    public AiMetrics metrics() {
        return metrics;
    }

    public AiSettings settings() {
        return settings;
    }

    public PiiRedactor redactor() {
        return redactor;
    }

    /** Learning loop: embed a resolved ticket so later tickets can match it. */
    public void indexResolvedTicket(String ticketId, String title, String description, String resolution,
                                    Category category) {
        String text = redactor.redactText(title + "\n" + description);
        String redactedResolution = redactor.redactText(resolution == null ? "" : resolution);
        float[] vector = gateway.embed(ticketId, text);
        store.upsert(new VectorDoc(VectorDoc.idFor(VectorDoc.Type.TICKET, ticketId), VectorDoc.Type.TICKET,
                ticketId, redactor.redactText(title), text, redactedResolution,
                category == null ? "OTHER" : category.name(), vector));
    }

    public void indexKbArticle(String articleId, String title, String content) {
        String text = redactor.redactText(title + "\n" + content);
        float[] vector = gateway.embed(null, text);
        store.upsert(new VectorDoc(VectorDoc.idFor(VectorDoc.Type.KB, articleId), VectorDoc.Type.KB,
                articleId, title, redactor.redactText(content), "", "KB", vector));
    }

    public void removeKbArticle(String articleId) {
        store.delete(VectorDoc.idFor(VectorDoc.Type.KB, articleId));
    }

    public void removeTicket(String ticketId) {
        store.delete(VectorDoc.idFor(VectorDoc.Type.TICKET, ticketId));
    }

    @Override
    public void close() {
        gateway.executor().close();
    }
}
