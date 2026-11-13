package com.ticketiq.app.ai;

import com.ticketiq.app.ticket.TicketService;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net: tickets that are still NEW after a short grace period (the AI was off, or the app restarted)
 * are triaged now. This is what makes "stop the AI, create a ticket, start the AI" end well.
 */
@Component
public class AiTriageSweeper {

    private static final long GRACE_SECONDS = 20;

    private final TicketService tickets;
    private final AiTriageHandler handler;
    private final AtomicBoolean aiSwitch;

    public AiTriageSweeper(TicketService tickets, AiTriageHandler handler, AtomicBoolean aiSwitch) {
        this.tickets = tickets;
        this.handler = handler;
        this.aiSwitch = aiSwitch;
    }

    @Scheduled(initialDelayString = "${ticketiq.ai.sweep-ms:30000}", fixedDelayString = "${ticketiq.ai.sweep-ms:30000}")
    public void sweep() {
        if (!aiSwitch.get()) {
            return;
        }
        List<Long> waiting = tickets.staleNewTicketIds(Instant.now().minusSeconds(GRACE_SECONDS));
        waiting.forEach(handler::triage);
    }
}
