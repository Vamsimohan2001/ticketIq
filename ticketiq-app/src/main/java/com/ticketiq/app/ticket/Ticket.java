package com.ticketiq.app.ticket;

import com.ticketiq.app.user.User;
import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * The core record of the app. The first block of fields is the v1 "legacy" ticket;
 * the {@code ai*} block is what version 2 adds, and it is only ever a suggestion.
 */
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_ticket_status", columnList = "status"),
        @Index(name = "idx_ticket_category", columnList = "category"),
        @Index(name = "idx_ticket_priority", columnList = "priority"),
        @Index(name = "idx_ticket_created", columnList = "createdAt")
})
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @jakarta.persistence.Column(unique = true, length = 40)
    private String seedKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category = Category.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    private SeverityBand severityBand = SeverityBand.B;

    private Integer expectedResolutionDays = SeverityBand.B.defaultDays();

    private Instant resolutionDueAt = Instant.now().plus(4, java.time.temporal.ChronoUnit.DAYS);

    private Boolean knowledgeAccessRequested = false;

    @Column(length = 4000)
    private String sharedIncidentIdsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriageSource triageSource = TriageSource.NONE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(length = 4000)
    private String resolution;

    @Column(length = 120)
    private String supportTeam;
    @Column(length = 4000)
    private String handlingNotes;
    @Column(length = 2000)
    private String selfService;
    private Integer resolutionDays;

    // ---- version 2: what the AI suggested ----
    @Column(length = 300)
    private String aiSummary;
    private Double aiConfidence;
    @Column(length = 400)
    private String aiRationale;
    private boolean aiNeedsHuman;
    @Column(length = 300)
    private String aiReason;
    @Column(length = 4000)
    private String aiSuggestedReply;
    @Column(length = 4000)
    private String aiSourcesJson;
    @Column(length = 4000)
    private String aiSimilarJson;
    private String aiProvider;
    private String aiModel;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    /** Optimistic locking: two agents editing the same ticket cannot silently overwrite each other. */
    @Version
    private Long version;

    protected Ticket() {
        // for JPA
    }

    public Ticket(String title, String description, User requester) {
        this.title = title.strip();
        this.description = description.strip();
        this.requester = requester;
    }

    public void updateDetails(String title, String description) {
        this.title = title.strip();
        this.description = description.strip();
    }

    public void clearAiSuggestions() {
        aiSummary = null;
        aiConfidence = null;
        aiRationale = null;
        aiNeedsHuman = false;
        aiReason = null;
        aiSuggestedReply = null;
        aiSourcesJson = null;
        aiSimilarJson = null;
        aiProvider = null;
        aiModel = null;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    // ---- plain getters and setters ----
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSeedKey() { return seedKey; }
    public void setSeedKey(String seedKey) { this.seedKey = seedKey; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public SeverityBand getSeverityBand() { return severityBand == null ? SeverityBand.B : severityBand; }
    public void setSeverityBand(SeverityBand severityBand) { this.severityBand = severityBand; }
    public Integer getExpectedResolutionDays() {
        return expectedResolutionDays == null ? getSeverityBand().defaultDays() : expectedResolutionDays;
    }
    public void setExpectedResolutionDays(Integer expectedResolutionDays) { this.expectedResolutionDays = expectedResolutionDays; }
    public Instant getResolutionDueAt() { return resolutionDueAt; }
    public void setResolutionDueAt(Instant resolutionDueAt) { this.resolutionDueAt = resolutionDueAt; }
    public boolean isKnowledgeAccessRequested() { return Boolean.TRUE.equals(knowledgeAccessRequested); }
    public void setKnowledgeAccessRequested(boolean knowledgeAccessRequested) { this.knowledgeAccessRequested = knowledgeAccessRequested; }
    public String getSharedIncidentIdsJson() { return sharedIncidentIdsJson; }
    public void setSharedIncidentIdsJson(String sharedIncidentIdsJson) { this.sharedIncidentIdsJson = sharedIncidentIdsJson; }
    public TriageSource getTriageSource() { return triageSource; }
    public void setTriageSource(TriageSource triageSource) { this.triageSource = triageSource; }
    public User getRequester() { return requester; }
    public User getAssignee() { return assignee; }
    public void setAssignee(User assignee) { this.assignee = assignee; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public String getSupportTeam() { return supportTeam; }
    public void setSupportTeam(String supportTeam) { this.supportTeam = supportTeam; }
    public String getHandlingNotes() { return handlingNotes; }
    public void setHandlingNotes(String handlingNotes) { this.handlingNotes = handlingNotes; }
    public String getSelfService() { return selfService; }
    public void setSelfService(String selfService) { this.selfService = selfService; }
    public Integer getResolutionDays() { return resolutionDays; }
    public void setResolutionDays(Integer resolutionDays) { this.resolutionDays = resolutionDays; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public Double getAiConfidence() { return aiConfidence; }
    public void setAiConfidence(Double aiConfidence) { this.aiConfidence = aiConfidence; }
    public String getAiRationale() { return aiRationale; }
    public void setAiRationale(String aiRationale) { this.aiRationale = aiRationale; }
    public boolean isAiNeedsHuman() { return aiNeedsHuman; }
    public void setAiNeedsHuman(boolean aiNeedsHuman) { this.aiNeedsHuman = aiNeedsHuman; }
    public String getAiReason() { return aiReason; }
    public void setAiReason(String aiReason) { this.aiReason = aiReason; }
    public String getAiSuggestedReply() { return aiSuggestedReply; }
    public void setAiSuggestedReply(String aiSuggestedReply) { this.aiSuggestedReply = aiSuggestedReply; }
    public String getAiSourcesJson() { return aiSourcesJson; }
    public void setAiSourcesJson(String aiSourcesJson) { this.aiSourcesJson = aiSourcesJson; }
    public String getAiSimilarJson() { return aiSimilarJson; }
    public void setAiSimilarJson(String aiSimilarJson) { this.aiSimilarJson = aiSimilarJson; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) { this.aiModel = aiModel; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
