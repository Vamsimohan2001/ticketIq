package com.ticketiq.app.ai;

import com.ticketiq.app.events.TicketEvents.KbChanged;
import com.ticketiq.app.events.TicketEvents.TicketResolved;
import com.ticketiq.app.kb.KbRepository;
import com.ticketiq.app.ticket.Ticket;
import com.ticketiq.app.ticket.TicketRepository;
import com.ticketiq.app.ticket.TicketStatus;
import com.ticketiq.core.ai.AiRuntime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The learning loop. Resolved tickets and knowledge-base articles are embedded into the vector store so
 * the next similar ticket can find them. The store lives in memory, so everything is re-indexed at startup.
 */
@Component
public class AiIndexer {

    private static final Logger log = LoggerFactory.getLogger(AiIndexer.class);

    private final AiRuntime ai;
    private final KbRepository kb;
    private final TicketRepository tickets;

    public AiIndexer(AiRuntime ai, KbRepository kb, TicketRepository tickets) {
        this.ai = ai;
        this.kb = kb;
        this.tickets = tickets;
    }

    @Async("aiExecutor")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void reindexAtStartup() {
        int articles = 0;
        int resolved = 0;
        for (var a : kb.findAll()) {
            try {
                ai.indexKbArticle(String.valueOf(a.getId()), a.getTitle(), a.getContent());
                articles++;
            } catch (RuntimeException e) {
                log.warn("Could not index article {}: {}", a.getId(), e.getMessage());
            }
        }
        List<Ticket> done = tickets.findByStatusIn(List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED));
        for (Ticket t : done) {
            if (t.getResolution() == null || t.getResolution().isBlank()) {
                continue;
            }
            try {
                ai.indexResolvedTicket(String.valueOf(t.getId()), t.getTitle(), t.getDescription(),
                        retrievalText(t), t.getCategory());
                resolved++;
            } catch (RuntimeException e) {
                log.warn("Could not index ticket {}: {}", t.getId(), e.getMessage());
            }
        }
        log.info("AI index ready: {} articles and {} resolved tickets", articles, resolved);
    }

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onTicketResolved(TicketResolved event) {
        tickets.findById(event.ticketId()).ifPresent(t -> {
            try {
                ai.indexResolvedTicket(String.valueOf(t.getId()), t.getTitle(), t.getDescription(),
                        retrievalText(t), t.getCategory());
                log.info("Learned from resolved ticket {}", t.getId());
            } catch (RuntimeException e) {
                log.warn("Could not learn from ticket {}: {}", t.getId(), e.getMessage());
            }
        });
    }

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onKbChanged(KbChanged event) {
        String id = String.valueOf(event.articleId());
        if (event.deleted()) {
            ai.removeKbArticle(id);
            return;
        }
        kb.findById(event.articleId()).ifPresent(a -> {
            try {
                ai.indexKbArticle(id, a.getTitle(), a.getContent());
            } catch (RuntimeException e) {
                log.warn("Could not index article {}: {}", id, e.getMessage());
            }
        });
    }

    private static String retrievalText(Ticket ticket) {
        return "Resolution: " + ticket.getResolution()
                + "\nHandling team: " + (ticket.getSupportTeam() == null
                ? com.ticketiq.app.ticket.TicketTeams.forCategory(ticket.getCategory()) : ticket.getSupportTeam())
                + "\nAgent handling: " + (ticket.getHandlingNotes() == null ? "" : ticket.getHandlingNotes())
                + "\nSafe self-service: " + (ticket.getSelfService() == null ? "" : ticket.getSelfService());
    }
}
