package com.ticketiq.app.ticket;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /** Tickets still waiting for the AI after a grace period (used by the retry sweeper). */
    List<Ticket> findTop20ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(TicketStatus status, Instant before);

    List<Ticket> findByStatusIn(Collection<TicketStatus> statuses);

    long countByTriageSource(TriageSource source);

    @Query("select t.status, count(t) from Ticket t group by t.status")
    List<Object[]> statusCounts();

    @Query("select t.category, count(t) from Ticket t group by t.category")
    List<Object[]> categoryCounts();

    @Query("select t.priority, count(t) from Ticket t group by t.priority")
    List<Object[]> priorityCounts();

    @Query("select avg(t.aiConfidence) from Ticket t where t.triageSource = com.ticketiq.app.ticket.TriageSource.AI")
    Double averageAiConfidence();
}
