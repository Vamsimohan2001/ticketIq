package com.ticketiq.app.ticket;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('AGENT','ADMIN')")
public class IncidentNotificationController {

    public record ReviewReminder(Long ticketId, String title, String team, Instant updatedAt, long hoursSinceUpdate) {
    }

    private final TicketRepository tickets;

    public IncidentNotificationController(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ReviewReminder> reminders() {
        Instant cutoff = Instant.now().minus(6, ChronoUnit.HOURS);
        return tickets.findByStatusNotInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED), cutoff)
                .stream().map(t -> new ReviewReminder(t.getId(), t.getTitle(),
                        t.getSupportTeam() == null ? TicketTeams.forCategory(t.getCategory()) : t.getSupportTeam(),
                        t.getUpdatedAt(), ChronoUnit.HOURS.between(t.getUpdatedAt(), Instant.now())))
                .toList();
    }
}
