package com.ticketiq.core.ai;

import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;
import java.util.List;

/**
 * Everything the AI learned about one ticket. Maps one-to-one onto the ticket.triaged event.
 *
 * @param suggestedReply null when there was no relevant context or the model failed
 * @param redactions     how many personal-data items were removed before the model saw the text
 */
public record PipelineResult(String ticketId, Category category, Priority priority, String summary,
                             double confidence, String rationale, boolean needsHuman, String reason,
                             List<SimilarTicket> similar, String suggestedReply, List<SourceRef> sources,
                             String provider, String model, long latencyMs, int redactions, boolean cached) {
}
