package com.ticketiq.app.ticket;

import com.ticketiq.app.common.BadRequestException;
import com.ticketiq.app.common.ForbiddenException;
import com.ticketiq.app.common.NotFoundException;
import com.ticketiq.app.security.AuthUser;
import com.ticketiq.core.json.Json;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeShareService {

    public record ShareInput(@jakarta.validation.constraints.NotNull @Size(max = 10) List<Long> incidentIds) {
    }

    public record SharedIncident(Long id, String title, String category, String supportTeam,
                                 String resolution, String handlingNotes, String selfService,
                                 Integer resolutionDays, Instant closedAt, double relevance) {
    }

    private final TicketRepository tickets;

    public KnowledgeShareService(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Transactional
    public void requestAccess(AuthUser caller, long ticketId) {
        Ticket current = visibleTicket(caller, ticketId);
        ensureOpen(current);
        if (caller.isStaff()) {
            throw new ForbiddenException("Only the incident requester can ask for knowledge access");
        }
        current.setKnowledgeAccessRequested(true);
        current.touch();
    }

    @Transactional(readOnly = true)
    public List<SharedIncident> recommendations(long ticketId) {
        Ticket current = find(ticketId);
        Map<Long, Double> scores = TicketDtos.similar(current).stream()
                .collect(java.util.stream.Collectors.toMap(TicketDtos.SimilarView::ticketId,
                        TicketDtos.SimilarView::score, Math::max));
        if (scores.isEmpty()) {
            return List.of();
        }
        List<Ticket> matches = tickets.findAllById(scores.keySet()).stream()
                .filter(t -> t.getStatus().isFinished() && t.getResolution() != null
                        && !t.getResolution().isBlank())
                .toList();
        return matches.stream().map(t -> toShared(t, scores.getOrDefault(t.getId(), 0.0))).toList();
    }

    @Transactional
    public void share(AuthUser caller, long ticketId, ShareInput input) {
        Ticket current = find(ticketId);
        ensureOpen(current);
        if (!current.isKnowledgeAccessRequested()) {
            throw new BadRequestException("The requester has not asked for past-incident details");
        }
        Map<Long, Double> recommendations = TicketDtos.similar(current).stream()
                .collect(java.util.stream.Collectors.toMap(TicketDtos.SimilarView::ticketId,
                        TicketDtos.SimilarView::score, Math::max));
        List<Long> ids = input.incidentIds().stream().distinct().toList();
        if (ids.size() != input.incidentIds().size() || ids.stream().anyMatch(id -> !recommendations.containsKey(id))) {
            throw new BadRequestException("Select only distinct incidents recommended for this issue");
        }
        List<Ticket> selected = tickets.findAllById(ids);
        if (selected.size() != ids.size() || selected.stream().anyMatch(t -> t.getId().equals(current.getId())
                || !t.getStatus().isFinished() || t.getResolution() == null || t.getResolution().isBlank())) {
            throw new BadRequestException("Only resolved or closed incidents can be shared");
        }
        current.setSharedIncidentIdsJson(ids.isEmpty() ? null : Json.write(ids));
        current.touch();
    }

    @Transactional(readOnly = true)
    public List<SharedIncident> shared(AuthUser caller, long ticketId) {
        Ticket current = visibleTicket(caller, ticketId);
        if (current.getStatus().isFinished() || current.getSharedIncidentIdsJson() == null
                || current.getSharedIncidentIdsJson().isBlank()) {
            return List.of();
        }
        List<Long> ids = Json.asList(Json.parse(current.getSharedIncidentIdsJson())).stream()
                .map(o -> ((Number) o).longValue()).toList();
        Map<Long, Double> scores = TicketDtos.similar(current).stream()
                .collect(java.util.stream.Collectors.toMap(TicketDtos.SimilarView::ticketId,
                        TicketDtos.SimilarView::score, Math::max));
        List<Ticket> selected = tickets.findAllById(ids);
        return selected.stream().map(t -> toShared(t, scores.getOrDefault(t.getId(), 0.0))).toList();
    }

    private static SharedIncident toShared(Ticket ticket, double score) {
        return new SharedIncident(ticket.getId(), ticket.getTitle(), ticket.getCategory().name(),
                ticket.getSupportTeam() == null ? TicketTeams.forCategory(ticket.getCategory()) : ticket.getSupportTeam(),
                ticket.getResolution(), ticket.getHandlingNotes(), ticket.getSelfService(),
                ticket.getResolutionDays(), ticket.getUpdatedAt(), score);
    }

    private Ticket visibleTicket(AuthUser caller, long id) {
        Ticket ticket = find(id);
        if (!caller.isStaff() && !ticket.getRequester().getId().equals(caller.id())) {
            throw new NotFoundException("Incident not found");
        }
        return ticket;
    }

    private Ticket find(long id) {
        return tickets.findById(id).orElseThrow(() -> new NotFoundException("Incident not found"));
    }

    private static void ensureOpen(Ticket ticket) {
        if (ticket.getStatus().isFinished()) {
            throw new BadRequestException("Past-incident details are available only while the incident is active");
        }
    }
}
