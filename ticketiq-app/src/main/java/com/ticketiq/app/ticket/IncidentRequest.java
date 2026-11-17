package com.ticketiq.app.ticket;

import com.ticketiq.app.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "incident_requests")
public class IncidentRequest {

    public enum Type { SEVERITY_CHANGE, DEADLINE_EXTENSION }
    public enum Status { WAITING_USER, PENDING_ADMIN, APPROVED, DECLINED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    private SeverityBand proposedSeverity;

    private Integer extensionDays;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected IncidentRequest() {
    }

    public IncidentRequest(Ticket ticket, User requester, Type type, Status status,
                           SeverityBand proposedSeverity, Integer extensionDays, String reason) {
        this.ticket = ticket;
        this.requester = requester;
        this.type = type;
        this.status = status;
        this.proposedSeverity = proposedSeverity;
        this.extensionDays = extensionDays;
        this.reason = reason.strip();
    }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public User getRequester() { return requester; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public SeverityBand getProposedSeverity() { return proposedSeverity; }
    public Integer getExtensionDays() { return extensionDays; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
