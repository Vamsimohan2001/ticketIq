package com.ticketiq.core.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Retrieval-augmented reply drafting (integration points IP-5 retrieval and IP-6 grounded generation).
 *
 * <p>Finds the most relevant knowledge-base articles and resolved tickets, hands only those to the model
 * and asks it to answer from them with source tags. No relevant context means no draft: we never let the
 * model improvise an answer.
 */
public final class ReplyDraftService {

    private static final int KB_TOP_K = 3;
    private static final int TICKET_TOP_K = 3;

    private final AiGateway gateway;
    private final VectorStore store;
    private final double contextThreshold;

    public ReplyDraftService(AiGateway gateway, VectorStore store, double contextThreshold) {
        this.gateway = gateway;
        this.store = store;
        this.contextThreshold = contextThreshold;
    }

    public Optional<SuggestedReply> draft(String ticketId, String redactedText, float[] vector) {
        List<VectorHit> hits = new ArrayList<>();
        hits.addAll(store.search(vector, KB_TOP_K, contextThreshold, VectorDoc.Type.KB, null));
        hits.addAll(store.search(vector, TICKET_TOP_K, contextThreshold, VectorDoc.Type.TICKET, ticketId));
        if (hits.isEmpty()) {
            return Optional.empty();
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));

        StringBuilder context = new StringBuilder();
        List<SourceRef> supplied = new ArrayList<>();
        for (VectorHit h : hits) {
            VectorDoc d = h.doc();
            String type = d.type() == VectorDoc.Type.KB ? "kb" : "ticket";
            supplied.add(new SourceRef(type, d.refId(), d.title(), SimilarityService.round(h.score())));
            context.append("[SOURCE ").append(type).append(':').append(d.refId()).append("] ")
                    .append(PromptTemplates.neutralise(d.title())).append('\n');
            String body = d.type() == VectorDoc.Type.KB ? d.text() : "Resolution: " + d.resolution();
            context.append(PromptTemplates.neutralise(body)).append("\n\n");
        }

        String system = PromptTemplates.load("reply-system.txt");
        String user = PromptTemplates.render("reply-user.txt", Map.of(
                "context", context.toString().strip(),
                "ticket", PromptTemplates.neutralise(redactedText)));
        try {
            String text = gateway.chat(AiCallRecord.Kind.REPLY, ticketId, system, user, false).strip();
            if (text.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new SuggestedReply(text, citedSources(text, supplied)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Keep only sources that were actually supplied and cited; if none are cited, keep what we supplied. */
    static List<SourceRef> citedSources(String text, List<SourceRef> supplied) {
        List<SourceRef> cited = supplied.stream().filter(s -> text.contains("[" + s.tag() + "]")).toList();
        return cited.isEmpty() ? supplied : cited;
    }
}
