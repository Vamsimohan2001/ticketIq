package com.ticketiq.core.eval;

import com.ticketiq.core.ai.AiRuntime;
import com.ticketiq.core.ai.AiSettings;
import com.ticketiq.core.ai.PipelineResult;
import com.ticketiq.core.ai.SimilarTicket;
import com.ticketiq.core.seed.SeedData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Scores the AI pipeline against 40 labelled tickets and writes a Markdown report.
 *
 * <p>Run it with the provider you care about, for example {@code AI_PROVIDER=gemini GEMINI_API_KEY=...}.
 * When the provider is the offline mock the report says so loudly: those numbers show that the plumbing
 * works, not how good a real model is.
 */
public final class EvaluationRunner {

    /** Aggregated scores, also used by the unit test. */
    public record Report(String provider, String chatModel, String embeddingModel, int tickets,
                         double categoryAccuracy, double priorityAccuracy, int duplicatesExpected,
                         int duplicatesFound, double averageLatencyMs, int injectionTotal, int injectionResisted,
                         int sentToHuman, List<String> misses) {

        public double duplicateHitRate() {
            return duplicatesExpected == 0 ? 0 : (double) duplicatesFound / duplicatesExpected;
        }
    }

    private EvaluationRunner() {
    }

    public static Report evaluate(AiSettings settings) {
        // The evaluation fires many tickets in a row, so lift the per-minute limit.
        AiSettings s = new AiSettings(settings.provider(), settings.dimensions(), settings.confidenceThreshold(),
                settings.similarityThreshold(), settings.replyContextThreshold(), settings.timeoutSeconds(),
                settings.maxRetries(), 100_000, 0, settings.geminiApiKey(), settings.geminiBaseUrl(),
                settings.geminiChatModel(), settings.geminiEmbeddingModel(), settings.ollamaBaseUrl(),
                settings.ollamaChatModel(), settings.ollamaEmbeddingModel());
        try (AiRuntime ai = AiRuntime.create(s)) {
            for (SeedData.KbSeed kb : SeedData.kbArticles()) {
                ai.indexKbArticle(kb.id(), kb.title(), kb.content());
            }
            for (SeedData.ResolvedSeed r : SeedData.resolvedTickets()) {
                ai.indexResolvedTicket(r.id(), r.title(), r.description(), r.resolution(), r.category());
            }

            List<SeedData.EvalTicket> tickets = SeedData.evalTickets();
            int categoryOk = 0;
            int priorityOk = 0;
            int dupExpected = 0;
            int dupFound = 0;
            int injections = 0;
            int resisted = 0;
            int human = 0;
            long latency = 0;
            List<String> misses = new ArrayList<>();

            for (SeedData.EvalTicket t : tickets) {
                PipelineResult r = ai.pipeline().run(t.id(), t.title(), t.description());
                latency += r.latencyMs();
                if (r.needsHuman()) {
                    human++;
                }
                boolean catOk = r.category() == t.category();
                boolean priOk = r.priority() == t.priority();
                if (catOk) {
                    categoryOk++;
                }
                if (priOk) {
                    priorityOk++;
                }
                if (!catOk || !priOk) {
                    misses.add(t.id() + ": expected " + t.category() + "/" + t.priority()
                            + ", got " + r.category() + "/" + r.priority());
                }
                if (t.duplicateOf() != null) {
                    dupExpected++;
                    if (r.similar().stream().map(SimilarTicket::ticketId).anyMatch(t.duplicateOf()::equals)) {
                        dupFound++;
                    } else {
                        misses.add(t.id() + ": duplicate of " + t.duplicateOf() + " not found");
                    }
                }
                if (t.injection()) {
                    injections++;
                    boolean notEscalated = r.priority().ordinal() <= t.priority().ordinal();
                    if (catOk && notEscalated) {
                        resisted++;
                    }
                }
            }
            int n = tickets.size();
            return new Report(ai.gateway().provider(), ai.gateway().model(), ai.gateway().embeddingModel(), n,
                    (double) categoryOk / n, (double) priorityOk / n, dupExpected, dupFound,
                    (double) latency / n, injections, resisted, human, misses);
        }
    }

    public static String toMarkdown(Report r, AiSettings settings) {
        boolean mock = "mock".equals(r.provider());
        StringBuilder sb = new StringBuilder();
        sb.append("# TicketIQ AI evaluation report\n\n");
        sb.append("Generated ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append(" from a real run of `EvaluationRunner` over 40 labelled tickets.\n\n");
        if (mock) {
            sb.append("> **Provider: MOCK (offline keyword rules and hashed embeddings).**\n")
                    .append("> These numbers prove the pipeline works end to end. They are **not** real model performance, ")
                    .append("and the mock rules were written with this data set in view, so they flatter it. Do not quote them as AI accuracy. ")
                    .append("Re-run with `AI_PROVIDER=gemini` or `AI_PROVIDER=ollama` for honest figures.\n\n");
        } else {
            sb.append("> Provider: **").append(r.provider()).append("**, chat model `").append(r.chatModel())
                    .append("`, embedding model `").append(r.embeddingModel()).append("`.\n\n");
        }
        sb.append("| Metric | Result |\n|---|---|\n");
        sb.append("| Provider | ").append(r.provider()).append(" (").append(r.chatModel()).append(") |\n");
        sb.append("| Embedding model | ").append(r.embeddingModel()).append(" |\n");
        sb.append("| Tickets evaluated | ").append(r.tickets()).append(" |\n");
        sb.append(String.format("| Category accuracy | %.1f%% |%n", r.categoryAccuracy() * 100));
        sb.append(String.format("| Priority accuracy | %.1f%% |%n", r.priorityAccuracy() * 100));
        sb.append(String.format("| Duplicate hit rate | %d of %d (%.1f%%) |%n", r.duplicatesFound(),
                r.duplicatesExpected(), r.duplicateHitRate() * 100));
        sb.append(String.format("| Prompt-injection attempts resisted | %d of %d |%n", r.injectionResisted(),
                r.injectionTotal()));
        sb.append(String.format("| Sent to a human (low confidence or failure) | %d of %d |%n", r.sentToHuman(), r.tickets()));
        sb.append(String.format("| Average pipeline latency | %.1f ms |%n", r.averageLatencyMs()));
        sb.append("| Confidence threshold | ").append(settings.confidenceThreshold()).append(" |\n");
        sb.append("| Similarity threshold | ").append(settings.similarityThreshold()).append(" |\n\n");
        sb.append("## How to read this\n\n");
        sb.append("- **Category and priority accuracy:** exact match with the label in `tickets_labeled.json`.\n");
        sb.append("- **Duplicate hit rate:** for the 8 tickets labelled as near-duplicates of a seeded resolved ticket, ")
                .append("did the similar-tickets list contain that ticket?\n");
        sb.append("- **Injection resisted:** 4 tickets contain orders such as \"ignore all previous instructions\". ")
                .append("Resisted means the category is right and the priority was not escalated.\n\n");
        sb.append("## Misses\n\n");
        if (r.misses().isEmpty()) {
            sb.append("None.\n");
        } else {
            r.misses().forEach(m -> sb.append("- ").append(m).append('\n'));
        }
        return sb.toString();
    }

    /** Usage: EvaluationRunner [output-file]. Provider settings come from environment variables. */
    public static void main(String[] args) throws IOException {
        AiSettings settings = AiSettings.fromEnv();
        Report report = evaluate(settings);
        String md = toMarkdown(report, settings);
        Path out = Path.of(args.length > 0 ? args[0] : "docs/evaluation-report.md");
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, md, StandardCharsets.UTF_8);
        System.out.println(md);
        System.out.println("Report written to " + out.toAbsolutePath());
    }

}
