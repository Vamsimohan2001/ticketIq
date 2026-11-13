package com.ticketiq.core.ai;

import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;
import com.ticketiq.core.redaction.PiiRedactor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * The whole AI flow for one ticket, step by step:
 * <ol>
 *   <li>S1 guard: rate limit</li>
 *   <li>S2 redact personal data</li>
 *   <li>S3 triage (cached by hash of the redacted text)</li>
 *   <li>S4 embed and find similar tickets</li>
 *   <li>S5 draft a reply from retrieved context (RAG)</li>
 *   <li>S6 guardrails decide whether a human must look</li>
 * </ol>
 * Audit and metrics (S7) happen inside the {@link AiGateway}. Each step fails on its own: a broken
 * model never stops a ticket, it only sends it to a person.
 */
public final class AiPipeline {

    private final PiiRedactor redactor;
    private final TriageService triage;
    private final SimilarityService similarity;
    private final ReplyDraftService replies;
    private final GuardrailPolicy guardrails;
    private final AiGateway gateway;
    private final AiMetrics metrics;
    private final RateLimiter rateLimiter;
    private final TtlCache<String, TriageResult> cache;

    public AiPipeline(PiiRedactor redactor, TriageService triage, SimilarityService similarity,
                      ReplyDraftService replies, GuardrailPolicy guardrails, AiGateway gateway,
                      AiMetrics metrics, int rateLimitPerMinute, Duration cacheTtl) {
        this.redactor = redactor;
        this.triage = triage;
        this.similarity = similarity;
        this.replies = replies;
        this.guardrails = guardrails;
        this.gateway = gateway;
        this.metrics = metrics;
        this.rateLimiter = new RateLimiter(rateLimitPerMinute, Duration.ofMinutes(1));
        this.cache = new TtlCache<>(cacheTtl, 1000);
    }

    public PipelineResult run(String ticketId, String title, String description) {
        long start = System.nanoTime();

        // S2: privacy gate. Nothing below ever sees the original text.
        PiiRedactor.Result redacted = redactor.redact(
                "Title: " + nullToEmpty(title).strip() + "\nDescription: " + nullToEmpty(description).strip());
        String text = redacted.text();

        // S1: rate limit (cheap protection against a flood of tickets burning the AI budget)
        if (!rateLimiter.tryAcquire("pipeline")) {
            metrics.recordRateLimited();
            metrics.recordPipeline(true);
            return fallback(ticketId, title, new GuardrailPolicy.Decision(true, "AI rate limit reached"),
                    start, redacted.total());
        }

        // S3: triage, with a cache so identical tickets do not cost a second model call
        String key = sha256(text);
        Optional<TriageResult> triaged = cache.get(key);
        boolean fromCache = triaged.isPresent();
        metrics.recordCache(fromCache);
        if (!fromCache) {
            triaged = triage.triage(ticketId, text);
            triaged.ifPresent(r -> cache.put(key, r));
        }

        // S4 + S5: embeddings, duplicates, grounded reply. Skipped quietly if embedding is unavailable.
        List<SimilarTicket> similar = List.of();
        SuggestedReply reply = null;
        try {
            float[] vector = gateway.embed(ticketId, text);
            similar = similarity.findSimilar(ticketId, vector);
            reply = replies.draft(ticketId, text, vector).orElse(null);
        } catch (RuntimeException e) {
            // embedding failed: keep whatever triage produced
        }

        // S6: guardrails
        GuardrailPolicy.Decision decision = guardrails.evaluate(triaged);
        metrics.recordPipeline(decision.needsHuman());

        long ms = (System.nanoTime() - start) / 1_000_000;
        if (triaged.isEmpty()) {
            return new PipelineResult(ticketId, Category.OTHER, Priority.MEDIUM, shorten(title), 0.0, "",
                    true, decision.reason(), similar, reply == null ? null : reply.text(),
                    reply == null ? List.of() : reply.sources(), gateway.provider(), gateway.model(), ms,
                    redacted.total(), false);
        }
        TriageResult t = triaged.get();
        return new PipelineResult(ticketId, t.category(), t.priority(), t.summary(), t.confidence(), t.rationale(),
                decision.needsHuman(), decision.reason(), similar, reply == null ? null : reply.text(),
                reply == null ? List.of() : reply.sources(), gateway.provider(), gateway.model(), ms,
                redacted.total(), fromCache);
    }

    private PipelineResult fallback(String ticketId, String title, GuardrailPolicy.Decision d, long start, int redactions) {
        long ms = (System.nanoTime() - start) / 1_000_000;
        return new PipelineResult(ticketId, Category.OTHER, Priority.MEDIUM, shorten(title), 0.0, "",
                d.needsHuman(), d.reason(), List.of(), null, List.of(), gateway.provider(), gateway.model(), ms,
                redactions, false);
    }

    private static String shorten(String title) {
        String t = nullToEmpty(title).strip();
        if (t.isEmpty()) {
            return "Support request";
        }
        return t.length() > TriageResult.MAX_SUMMARY ? t.substring(0, TriageResult.MAX_SUMMARY - 3) + "..." : t;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
