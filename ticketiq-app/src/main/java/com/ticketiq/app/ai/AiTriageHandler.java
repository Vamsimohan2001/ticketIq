package com.ticketiq.app.ai;

import com.ticketiq.app.events.TicketEvents.TicketCreated;
import com.ticketiq.app.ticket.TicketService;
import com.ticketiq.core.ai.AiRuntime;
import com.ticketiq.core.ai.PipelineResult;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * INTEGRATION POINT: this is where a new ticket meets the AI pipeline.
 *
 * <p>ticket.created (after commit) -> run the pipeline on a background thread -> store the result
 * and publish ticket.triaged. If the AI is switched off or fails, the ticket simply stays as it is
 * and a person (or the retry sweeper) picks it up later.
 */
@Component
public class AiTriageHandler {

    private static final Logger log = LoggerFactory.getLogger(AiTriageHandler.class);

    private final AiRuntime ai;
    private final TicketService tickets;
    private final AtomicBoolean aiSwitch;

    public AiTriageHandler(AiRuntime ai, TicketService tickets, AtomicBoolean aiSwitch) {
        this.ai = ai;
        this.tickets = tickets;
        this.aiSwitch = aiSwitch;
    }

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketCreated(TicketCreated event) {
        triage(event.ticketId());
    }

    /** Runs the whole pipeline for one ticket. Never throws: a failure must not break anything else. */
    public void triage(long ticketId) {
        if (!aiSwitch.get()) {
            log.info("AI is switched off, ticket {} stays NEW until it is back", ticketId);
            return;
        }
        try {
            Optional<String[]> input = tickets.loadForTriage(ticketId);
            if (input.isEmpty()) {
                return;
            }
            log.info("AI triage starting for ticket {}", ticketId);
            PipelineResult result = ai.pipeline().run(String.valueOf(ticketId), input.get()[0], input.get()[1]);
            tickets.applyAiTriage(ticketId, result);
            log.info("AI triage done for ticket {}: {}/{} confidence {} needsHuman={} ({} ms)", ticketId,
                    result.category(), result.priority(), result.confidence(), result.needsHuman(),
                    result.latencyMs());
        } catch (RuntimeException e) {
            log.error("AI triage failed for ticket {}; leaving it for a human", ticketId, e);
        }
    }
}
