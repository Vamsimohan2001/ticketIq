package com.ticketiq.app.ticket;

import com.ticketiq.app.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** A reply on a ticket. Internal comments are agent notes that the requester never sees. */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, length = 4000)
    private String body;

    private boolean internal;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Comment() {
        // for JPA
    }

    public Comment(Ticket ticket, User author, String body, boolean internal) {
        this.ticket = ticket;
        this.author = author;
        this.body = body.strip();
        this.internal = internal;
    }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public User getAuthor() { return author; }
    public String getBody() { return body; }
    public boolean isInternal() { return internal; }
    public Instant getCreatedAt() { return createdAt; }
}
