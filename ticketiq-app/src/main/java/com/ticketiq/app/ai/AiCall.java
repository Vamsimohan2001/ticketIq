package com.ticketiq.app.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Audit row for one model call: who answered, how long it took, whether it worked. Never holds ticket text. */
@Entity
@Table(name = "ai_calls")
public class AiCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ticketId;

    @Column(nullable = false)
    private String kind;

    private String provider;
    private String model;
    private long latencyMs;
    private boolean success;

    @Column(length = 300)
    private String error;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AiCall() {
        // for JPA
    }

    public AiCall(Long ticketId, String kind, String provider, String model, long latencyMs, boolean success,
                  String error, Instant createdAt) {
        this.ticketId = ticketId;
        this.kind = kind;
        this.provider = provider;
        this.model = model;
        this.latencyMs = latencyMs;
        this.success = success;
        this.error = error;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public String getKind() { return kind; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public long getLatencyMs() { return latencyMs; }
    public boolean isSuccess() { return success; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
}
