package com.ticketiq.app.stats;

import com.ticketiq.app.ticket.AiFeedbackRepository;
import com.ticketiq.app.ticket.TicketRepository;
import com.ticketiq.app.ticket.TriageSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Numbers for the Insights page: the ticket mix, and how much the AI is actually helping. */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final TicketRepository tickets;
    private final AiFeedbackRepository feedback;

    public StatsController(TicketRepository tickets, AiFeedbackRepository feedback) {
        this.tickets = tickets;
        this.feedback = feedback;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        long total = tickets.count();
        long byAi = tickets.countByTriageSource(TriageSource.AI);
        long byHuman = tickets.countByTriageSource(TriageSource.MANUAL);
        long accepted = feedback.countByAccepted(true);
        long rejected = feedback.countByAccepted(false);
        Double avgConfidence = tickets.averageAiConfidence();

        Map<String, Object> ai = new LinkedHashMap<>();
        ai.put("triagedByAi", byAi);
        ai.put("triagedByHuman", byHuman);
        ai.put("averageConfidence", avgConfidence == null ? null : Math.round(avgConfidence * 100.0) / 100.0);
        ai.put("feedbackAccepted", accepted);
        ai.put("feedbackRejected", rejected);
        ai.put("acceptanceRate", accepted + rejected == 0 ? null
                : Math.round(100.0 * accepted / (accepted + rejected)) / 100.0);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalTickets", total);
        out.put("byStatus", toMap(tickets.statusCounts()));
        out.put("byCategory", toMap(tickets.categoryCounts()));
        out.put("byPriority", toMap(tickets.priorityCounts()));
        out.put("ai", ai);
        return out;
    }

    private static Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Object[] row : rows) {
            m.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return m;
    }
}
