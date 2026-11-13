package com.ticketiq.app.kb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A help article. The AI uses these as trusted sources when it drafts replies. */
@Entity
@Table(name = "kb_articles")
public class KbArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(length = 200)
    private String tags;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected KbArticle() {
        // for JPA
    }

    public KbArticle(String title, String content, String tags) {
        update(title, content, tags);
    }

    public void update(String title, String content, String tags) {
        this.title = title.strip();
        this.content = content.strip();
        this.tags = tags == null ? "" : tags.strip();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getTags() { return tags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
