package com.ticketiq.app.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** An agent's verdict on the AI's help for one ticket. This is how we measure whether the AI is useful. */
@Entity
@Table(name = "ai_feedback")
public class AiFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ticketId;

    @Column(nullable = false)
    private Long agentId;

    private boolean accepted;

    private boolean edited;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AiFeedback() {
        // for JPA
    }

    public AiFeedback(Long ticketId, Long agentId, boolean accepted, boolean edited) {
        this.ticketId = ticketId;
        this.agentId = agentId;
        this.accepted = accepted;
        this.edited = edited;
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getAgentId() { return agentId; }
    public boolean isAccepted() { return accepted; }
    public boolean isEdited() { return edited; }
    public Instant getCreatedAt() { return createdAt; }
}
