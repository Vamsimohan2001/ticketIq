package com.ticketiq.app.kb;

import com.ticketiq.app.common.NotFoundException;
import com.ticketiq.app.events.TicketEvents.KbChanged;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Knowledge-base CRUD. The article list is cached; every write clears the cache and tells the AI. */
@Service
public class KbService {

    public record KbRequest(@NotBlank @Size(max = 200) String title,
                            @NotBlank @Size(max = 4000) String content,
                            @Size(max = 200) String tags) {
    }

    public record KbResponse(Long id, String title, String content, String tags, Instant updatedAt) {
        static KbResponse from(KbArticle a) {
            return new KbResponse(a.getId(), a.getTitle(), a.getContent(), a.getTags(), a.getUpdatedAt());
        }
    }

    private final KbRepository repo;
    private final ApplicationEventPublisher events;

    public KbService(KbRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    @Cacheable("kbArticles")
    @Transactional(readOnly = true)
    public List<KbResponse> list() {
        return repo.findAllByOrderByTitleAsc().stream().map(KbResponse::from).toList();
    }

    @CacheEvict(value = "kbArticles", allEntries = true)
    @Transactional
    public KbResponse create(KbRequest req) {
        KbArticle saved = repo.save(new KbArticle(req.title(), req.content(), req.tags()));
        events.publishEvent(new KbChanged(saved.getId(), false));
        return KbResponse.from(saved);
    }

    @CacheEvict(value = "kbArticles", allEntries = true)
    @Transactional
    public KbResponse update(Long id, KbRequest req) {
        KbArticle article = repo.findById(id).orElseThrow(() -> new NotFoundException("Article not found"));
        article.update(req.title(), req.content(), req.tags());
        events.publishEvent(new KbChanged(id, false));
        return KbResponse.from(article);
    }

    @CacheEvict(value = "kbArticles", allEntries = true)
    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Article not found");
        }
        repo.deleteById(id);
        events.publishEvent(new KbChanged(id, true));
    }
}
