package com.ticketiq.app.ticket;

import com.ticketiq.app.security.AuthUser;
import com.ticketiq.app.user.User;
import com.ticketiq.core.json.Json;
import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response shapes for the ticket API. */
public final class TicketDtos {

    private TicketDtos() {
    }

    // ---------------------------------------------------------------- requests
    public record CreateTicketRequest(@NotBlank @Size(max = 200) String title,
                                      @NotBlank @Size(max = 4000) String description) {
    }

    public record UpdateTicketRequest(@NotBlank @Size(max = 200) String title,
                                      @NotBlank @Size(max = 4000) String description) {
    }

    public record TriageRequest(@NotNull Category category, @NotNull Priority priority) {
    }

    public record AssignRequest(Long assigneeId) {
    }

    public record StatusRequest(@NotNull TicketStatus status, @Size(max = 4000) String resolution) {
    }

    public record TimelineRequest(@NotNull SeverityBand severityBand,
                                  @Min(2) @Max(7) int expectedResolutionDays) {
    }

    public record CommentRequest(@NotBlank @Size(max = 4000) String body, boolean internal) {
    }

    public record FeedbackRequest(boolean accepted, boolean edited) {
    }

    // --------------------------------------------------------------- responses
    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    }

    public record TicketSummary(Long id, String title, TicketStatus status, Category category, Priority priority,
                                String suggestedTeam, SeverityBand severityBand, Integer expectedResolutionDays,
                                Instant resolutionDueAt, boolean knowledgeAccessRequested, TriageSource triageSource,
                                String aiSummary, Double aiConfidence,
                                boolean needsHuman, String requesterName, String assigneeName,
                                Instant createdAt, Instant updatedAt) {
        static TicketSummary from(Ticket t, boolean includeAi) {
            return new TicketSummary(t.getId(), t.getTitle(), t.getStatus(), t.getCategory(), t.getPriority(),
                    team(t), t.getSeverityBand(), t.getExpectedResolutionDays(), dueAt(t),
                    t.isKnowledgeAccessRequested(), t.getTriageSource(), includeAi ? t.getAiSummary() : null,
                    includeAi ? t.getAiConfidence() : null, includeAi && t.isAiNeedsHuman(),
                    t.getRequester().getFullName(),
                    t.getAssignee() == null ? null : t.getAssignee().getFullName(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    public record SimilarView(Long ticketId, String title, double score) {
    }

    public record SourceView(String type, String id, String title, double score) {
    }

    /** The AI block of a ticket. Only staff ever receive it. */
    public record AiInsight(String summary, Double confidence, String rationale, boolean needsHuman, String reason,
                            List<SimilarView> similar, List<SourceView> sources, boolean hasSuggestedReply,
                            String provider, String model) {
    }

    public record PublicInsight(String warning, String issueSummary, String possibleReason, String likelyTeam) {
    }

    public record TicketDetail(Long id, String title, String description, TicketStatus status, Category category,
                               Priority priority, String suggestedTeam, SeverityBand severityBand,
                               Integer expectedResolutionDays, Instant resolutionDueAt, TriageSource triageSource,
                               String requesterName, String requesterUserId, String requesterEmployeeId,
                               Long assigneeId, String assigneeName, String assigneeTeam, String resolution,
                               String handlingNotes, String selfService, boolean knowledgeAccessRequested,
                               boolean knowledgeDataShared,
                               Instant createdAt, Instant updatedAt, PublicInsight publicInsight, AiInsight ai) {
        static TicketDetail from(Ticket t, AuthUser viewer) {
            User assignee = t.getAssignee();
            AiInsight ai = viewer.isStaff() && t.getAiSummary() != null ? insight(t) : null;
            PublicInsight publicInsight = !viewer.isStaff() && t.getAiSummary() != null
                    ? new PublicInsight("AI-generated and may be incorrect. This is informational only, not a diagnosis or fix.",
                    t.getAiSummary(), t.getAiRationale(), team(t)) : null;
            return new TicketDetail(t.getId(), t.getTitle(), t.getDescription(), t.getStatus(), t.getCategory(),
                    t.getPriority(), team(t), t.getSeverityBand(), t.getExpectedResolutionDays(), dueAt(t),
                    t.getTriageSource(),
                    t.getRequester().getFullName(), t.getRequester().getUserId(), t.getRequester().getEmployeeId(),
                    assignee == null ? null : assignee.getId(),
                    assignee == null ? null : assignee.getFullName(), assignee == null ? null : assignee.getTeam(),
                    t.getResolution(), t.getHandlingNotes(), t.getSelfService(), t.isKnowledgeAccessRequested(),
                    t.getSharedIncidentIdsJson() != null && !t.getSharedIncidentIdsJson().isBlank(),
                    t.getCreatedAt(),
                    t.getUpdatedAt(), publicInsight, ai);
        }
    }

    public record CommentResponse(Long id, String authorName, String authorRole, String body, boolean internal,
                                  Instant createdAt) {
        static CommentResponse from(Comment c) {
            return new CommentResponse(c.getId(), c.getAuthor().getFullName(), c.getAuthor().getRole().name(),
                    c.getBody(), c.isInternal(), c.getCreatedAt());
        }
    }

    public record SuggestedReplyView(boolean available, String text, List<SourceView> sources) {
    }

    // ------------------------------------------------------------------ helpers
    static AiInsight insight(Ticket t) {
        return new AiInsight(t.getAiSummary(), t.getAiConfidence(), t.getAiRationale(), t.isAiNeedsHuman(),
                t.getAiReason(), similar(t), sources(t), t.getAiSuggestedReply() != null,
                t.getAiProvider(), t.getAiModel());
    }

    static List<SimilarView> similar(Ticket t) {
        if (t.getAiSimilarJson() == null || t.getAiSimilarJson().isBlank()) {
            return List.of();
        }
        return Json.asList(Json.parse(t.getAiSimilarJson())).stream().map(o -> {
            Map<String, Object> m = Json.asMap(o);
            return new SimilarView(Json.number(m, "ticketId").longValue(), Json.string(m, "title"),
                    Json.number(m, "score"));
        }).toList();
    }

    static List<SourceView> sources(Ticket t) {
        if (t.getAiSourcesJson() == null || t.getAiSourcesJson().isBlank()) {
            return List.of();
        }
        return Json.asList(Json.parse(t.getAiSourcesJson())).stream().map(o -> {
            Map<String, Object> m = Json.asMap(o);
            return new SourceView(Json.string(m, "type"), Json.string(m, "id"), Json.string(m, "title"),
                    Json.number(m, "score"));
        }).toList();
    }

    private static String team(Ticket t) {
        return t.getSupportTeam() == null ? TicketTeams.forCategory(t.getCategory()) : t.getSupportTeam();
    }

    private static Instant dueAt(Ticket t) {
        return t.getResolutionDueAt() == null
                ? t.getCreatedAt().plusSeconds(t.getExpectedResolutionDays() * 86_400L) : t.getResolutionDueAt();
    }
}
