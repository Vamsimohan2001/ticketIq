package com.ticketiq.app.ticket;

import com.ticketiq.app.common.BadRequestException;
import com.ticketiq.app.common.ForbiddenException;
import com.ticketiq.app.common.NotFoundException;
import com.ticketiq.app.security.AuthUser;
import com.ticketiq.app.user.User;
import com.ticketiq.app.user.UserRepository;
import com.ticketiq.core.model.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentRequestService {

    public record RequestInput(@NotNull IncidentRequest.Type type, SeverityBand proposedSeverity,
                               @Min(1) @Max(14) Integer extensionDays,
                               @NotBlank @Size(max = 1000) String reason) {
    }

    public record UserDecision(@NotNull Boolean approved) {
    }

    public record AdminDecision(@NotNull Boolean approved) {
    }

    public record RequestView(Long id, Long ticketId, String ticketTitle, String requesterName,
                              IncidentRequest.Type type, IncidentRequest.Status status,
                              SeverityBand proposedSeverity, Integer extensionDays, String reason,
                              Instant createdAt) {
        static RequestView from(IncidentRequest request) {
            return new RequestView(request.getId(), request.getTicket().getId(), request.getTicket().getTitle(),
                    request.getRequester().getFullName(), request.getType(), request.getStatus(),
                    request.getProposedSeverity(), request.getExtensionDays(), request.getReason(),
                    request.getCreatedAt());
        }
    }

    private final TicketRepository tickets;
    private final IncidentRequestRepository requests;
    private final UserRepository users;

    public IncidentRequestService(TicketRepository tickets, IncidentRequestRepository requests, UserRepository users) {
        this.tickets = tickets;
        this.requests = requests;
        this.users = users;
    }

    @Transactional
    public RequestView create(AuthUser caller, long ticketId, RequestInput input) {
        Ticket ticket = findTicket(ticketId);
        ensureOpen(ticket);
        if (!caller.isStaff() && !ticket.getRequester().getId().equals(caller.id())) {
            throw new NotFoundException("Incident not found");
        }
        if (input.type() == IncidentRequest.Type.SEVERITY_CHANGE && input.proposedSeverity() == null) {
            throw new BadRequestException("Select the requested severity band");
        }
        if (input.type() == IncidentRequest.Type.DEADLINE_EXTENSION
                && (input.extensionDays() == null || input.extensionDays() < 1 || input.extensionDays() > 14)) {
            throw new BadRequestException("An extension must be between 1 and 14 days");
        }
        if (input.type() == IncidentRequest.Type.SEVERITY_CHANGE && input.extensionDays() != null) {
            throw new BadRequestException("Severity change requests cannot include extension days");
        }
        User requester = users.findById(caller.id()).orElseThrow(() -> new NotFoundException("User not found"));
        IncidentRequest.Status status = caller.role() == Role.AGENT
                && input.type() == IncidentRequest.Type.SEVERITY_CHANGE
                ? IncidentRequest.Status.WAITING_USER : IncidentRequest.Status.PENDING_ADMIN;
        IncidentRequest saved = requests.save(new IncidentRequest(ticket, requester, input.type(), status,
                input.proposedSeverity(), input.extensionDays(), input.reason()));
        return RequestView.from(saved);
    }

    @Transactional
    public RequestView userDecision(AuthUser caller, long ticketId, long requestId, UserDecision decision) {
        IncidentRequest request = findRequest(requestId);
        if (!request.getTicket().getId().equals(ticketId)
                || !request.getTicket().getRequester().getId().equals(caller.id())) {
            throw new NotFoundException("Request not found");
        }
        if (request.getStatus() != IncidentRequest.Status.WAITING_USER) {
            throw new BadRequestException("This request is not waiting for your decision");
        }
        request.setStatus(decision.approved()
                ? IncidentRequest.Status.PENDING_ADMIN : IncidentRequest.Status.DECLINED);
        return RequestView.from(request);
    }

    @Transactional(readOnly = true)
    public List<RequestView> pendingAdmin() {
        return requests.findAllByStatusOrderByCreatedAtAsc(IncidentRequest.Status.PENDING_ADMIN)
                .stream().map(RequestView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RequestView> waitingUser(AuthUser caller, long ticketId) {
        Ticket ticket = findTicket(ticketId);
        if (caller.isStaff() || !ticket.getRequester().getId().equals(caller.id())) {
            throw new NotFoundException("Incident not found");
        }
        return requests.findAllByTicketIdAndStatusOrderByCreatedAtAsc(ticketId, IncidentRequest.Status.WAITING_USER)
                .stream().map(RequestView::from).toList();
    }

    @Transactional
    public RequestView adminDecision(long requestId, AdminDecision decision) {
        IncidentRequest request = findRequest(requestId);
        if (request.getStatus() != IncidentRequest.Status.PENDING_ADMIN) {
            throw new BadRequestException("This request is not pending administrator review");
        }
        if (decision.approved()) {
            apply(request);
            request.setStatus(IncidentRequest.Status.APPROVED);
        } else {
            request.setStatus(IncidentRequest.Status.DECLINED);
        }
        return RequestView.from(request);
    }

    private void apply(IncidentRequest request) {
        Ticket ticket = request.getTicket();
        ensureOpen(ticket);
        if (request.getType() == IncidentRequest.Type.SEVERITY_CHANGE) {
            SeverityBand band = request.getProposedSeverity();
            int days = band.defaultDays();
            ticket.setSeverityBand(band);
            ticket.setExpectedResolutionDays(days);
            ticket.setResolutionDueAt(Instant.now().plus(days, ChronoUnit.DAYS));
        } else {
            int extraDays = request.getExtensionDays();
            ticket.setExpectedResolutionDays(ticket.getExpectedResolutionDays() + extraDays);
            Instant due = ticket.getResolutionDueAt() == null
                    ? Instant.now().plus(ticket.getExpectedResolutionDays() - extraDays, ChronoUnit.DAYS)
                    : ticket.getResolutionDueAt();
            ticket.setResolutionDueAt(due.plus(extraDays, ChronoUnit.DAYS));
        }
        ticket.touch();
    }

    private Ticket findTicket(long id) {
        return tickets.findById(id).orElseThrow(() -> new NotFoundException("Incident not found"));
    }

    private IncidentRequest findRequest(long id) {
        return requests.findById(id).orElseThrow(() -> new NotFoundException("Request not found"));
    }

    private static void ensureOpen(Ticket ticket) {
        if (ticket.getStatus().isFinished()) {
            throw new BadRequestException("Closed incidents cannot be changed");
        }
    }
}
