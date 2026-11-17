package com.ticketiq.app.ticket;

import com.ticketiq.app.common.BadRequestException;
import com.ticketiq.app.common.ForbiddenException;
import com.ticketiq.app.common.NotFoundException;
import com.ticketiq.app.events.TicketEvents.TicketCreated;
import com.ticketiq.app.events.TicketEvents.TicketResolved;
import com.ticketiq.app.security.AuthUser;
import com.ticketiq.app.ticket.TicketDtos.CommentRequest;
import com.ticketiq.app.ticket.TicketDtos.CommentResponse;
import com.ticketiq.app.ticket.TicketDtos.CreateTicketRequest;
import com.ticketiq.app.ticket.TicketDtos.FeedbackRequest;
import com.ticketiq.app.ticket.TicketDtos.PageResponse;
import com.ticketiq.app.ticket.TicketDtos.SimilarView;
import com.ticketiq.app.ticket.TicketDtos.StatusRequest;
import com.ticketiq.app.ticket.TicketDtos.SuggestedReplyView;
import com.ticketiq.app.ticket.TicketDtos.TicketDetail;
import com.ticketiq.app.ticket.TicketDtos.TicketSummary;
import com.ticketiq.app.ticket.TicketDtos.TriageRequest;
import com.ticketiq.app.ticket.TicketDtos.UpdateTicketRequest;
import com.ticketiq.app.ticket.TicketDtos.TimelineRequest;
import com.ticketiq.app.user.User;
import com.ticketiq.app.user.UserRepository;
import com.ticketiq.core.ai.PipelineResult;
import com.ticketiq.core.json.Json;
import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;
import com.ticketiq.core.model.Role;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All ticket business rules in one place. The top half is the v1 "legacy" behaviour (create, list,
 * manual triage, assign, status, comments). The bottom half is what v2 adds for the AI.
 * The AI is reached only through events, so none of this code ever waits for a model.
 */
@Service
public class TicketService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final AiFeedbackRepository feedback;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public TicketService(TicketRepository tickets, CommentRepository comments, AiFeedbackRepository feedback,
                         UserRepository users, ApplicationEventPublisher events) {
        this.tickets = tickets;
        this.comments = comments;
        this.feedback = feedback;
        this.users = users;
        this.events = events;
    }

    // ================================================================ v1: the classic helpdesk

    @Transactional
    public TicketDetail create(AuthUser caller, CreateTicketRequest req) {
        User requester = users.findById(caller.id()).orElseThrow(() -> new NotFoundException("User not found"));
        Ticket saved = tickets.save(new Ticket(req.title(), req.description(), requester));
        // Handled only after this transaction commits, so the AI never sees a ticket that could roll back.
        events.publishEvent(new TicketCreated(saved.getId()));
        return TicketDetail.from(saved, caller);
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketSummary> list(AuthUser caller, TicketStatus status, Category category,
                                            Priority priority, String text, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Specification<Ticket> spec = Specification.where(TicketSpecs.status(status))
                .and(TicketSpecs.category(category))
                .and(TicketSpecs.priority(priority))
                .and(TicketSpecs.titleContains(text))
                // Users can follow their own incidents through closure; staff can browse history.
                .and(caller.isStaff() ? null : TicketSpecs.requester(caller.id()));
        Page<Ticket> result = tickets.findAll(spec,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<TicketSummary> items = result.getContent().stream()
                .map(t -> TicketSummary.from(t, caller.isStaff())).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TicketDetail get(AuthUser caller, Long id) {
        return TicketDetail.from(visibleTicket(caller, id), caller);
    }

    @Transactional
    public TicketDetail updateDetails(AuthUser caller, Long id, UpdateTicketRequest request) {
        Ticket ticket = visibleTicket(caller, id);
        if (ticket.getStatus().isFinished()) {
            throw new BadRequestException("Closed incidents cannot be edited");
        }
        ticket.updateDetails(request.title(), request.description());
        ticket.clearAiSuggestions();
        if (ticket.getStatus().isOpenForTriage()) {
            ticket.setStatus(TicketStatus.NEW);
            ticket.setTriageSource(TriageSource.NONE);
        }
        ticket.touch();
        events.publishEvent(new TicketCreated(ticket.getId()));
        return TicketDetail.from(ticket, caller);
    }

    /** The v1 behaviour: an agent sets category and priority by hand. Always overrides the AI. */
    @Transactional
    public TicketDetail manualTriage(AuthUser caller, Long id, TriageRequest req) {
        Ticket t = find(id);
        t.setCategory(req.category());
        t.setPriority(req.priority());
        t.setTriageSource(TriageSource.MANUAL);
        if (t.getStatus().isOpenForTriage()) {
            t.setStatus(TicketStatus.TRIAGED);
        }
        t.touch();
        return TicketDetail.from(t, caller);
    }

    @Transactional
    public TicketDetail assign(AuthUser caller, Long id, Long assigneeId) {
        Ticket t = find(id);
        if (assigneeId == null) {
            t.setAssignee(null);
        } else {
            User assignee = users.findById(assigneeId).orElseThrow(() -> new NotFoundException("Assignee not found"));
            if (assignee.getRole() == Role.USER) {
                throw new BadRequestException("Tickets can only be assigned to agents or admins");
            }
            t.setAssignee(assignee);
            if (t.getStatus() == TicketStatus.TRIAGED || t.getStatus() == TicketStatus.NEEDS_HUMAN_TRIAGE) {
                t.setStatus(TicketStatus.IN_PROGRESS);
            }
        }
        t.touch();
        return TicketDetail.from(t, caller);
    }

    @Transactional
    public TicketDetail updateTimeline(AuthUser caller, Long id, TimelineRequest request) {
        if (caller.role() != Role.ADMIN) {
            throw new ForbiddenException("Only an administrator can edit an incident timeline");
        }
        Ticket ticket = find(id);
        int days = request.expectedResolutionDays();
        boolean validDays = switch (request.severityBand()) {
            case A -> days == 2;
            case B -> days >= 2 && days <= 4;
            case C -> days >= 5 && days <= 7;
        };
        if (!validDays) {
            throw new BadRequestException("Resolution targets are A: 2 days, B: 2-4 days, and C: 5-7 days");
        }
        ticket.setSeverityBand(request.severityBand());
        ticket.setExpectedResolutionDays(days);
        ticket.setResolutionDueAt(Instant.now().plusSeconds(days * 86_400L));
        ticket.touch();
        return TicketDetail.from(ticket, caller);
    }

    @Transactional
    public TicketDetail updateStatus(AuthUser caller, Long id, StatusRequest req) {
        Ticket t = find(id);
        TicketStatus target = req.status();
        boolean newlyFinished = target.isFinished() && !t.getStatus().isFinished();
        boolean reopening = t.getStatus().isFinished() && !target.isFinished();
        if (reopening && caller.role() != Role.ADMIN) {
            throw new ForbiddenException("Only an administrator can reopen a closed incident");
        }
        if (reopening) {
            t.setResolution(null);
        }
        if (target.isFinished()) {
            if (req.resolution() == null || req.resolution().isBlank()) {
                throw new BadRequestException("Please add completion remarks before closing the incident");
            }
            t.setResolution(req.resolution().strip());
        }
        t.setStatus(target);
        if (newlyFinished) {
            t.setSharedIncidentIdsJson(null);
            t.setKnowledgeAccessRequested(false);
        }
        if (newlyFinished) {
            t.setSharedIncidentIdsJson(null);
            t.setKnowledgeAccessRequested(false);
        }
        if (newlyFinished) {
            t.setSharedIncidentIdsJson(null);
            t.setKnowledgeAccessRequested(false);
        }
        t.touch();
        if (newlyFinished) {
            events.publishEvent(new TicketResolved(t.getId()));
        }
        return TicketDetail.from(t, caller);
    }

    @Transactional
    public CommentResponse addComment(AuthUser caller, Long id, CommentRequest req) {
        Ticket t = visibleTicket(caller, id);
        User author = users.findById(caller.id()).orElseThrow(() -> new NotFoundException("User not found"));
        boolean internal = req.internal() && caller.isStaff();   // users cannot write internal notes
        Comment saved = comments.save(new Comment(t, author, req.body(), internal));
        t.touch();
        return CommentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> comments(AuthUser caller, Long id) {
        visibleTicket(caller, id);
        List<Comment> found = caller.isStaff()
                ? comments.findByTicketIdOrderByCreatedAtAsc(id)
                : comments.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(id);
        return found.stream().map(CommentResponse::from).toList();
    }

    // ================================================================ v2: the AI layer

    @Transactional(readOnly = true)
    public List<SimilarView> similar(Long id) {
        return TicketDtos.similar(find(id));
    }

    @Transactional(readOnly = true)
    public SuggestedReplyView suggestedReply(Long id) {
        Ticket t = find(id);
        if (t.getAiSuggestedReply() == null) {
            return new SuggestedReplyView(false, null, List.of());
        }
        return new SuggestedReplyView(true, t.getAiSuggestedReply(), TicketDtos.sources(t));
    }

    @Transactional
    public void recordFeedback(AuthUser caller, Long id, FeedbackRequest req) {
        find(id);
        feedback.save(new AiFeedback(id, caller.id(), req.accepted(), req.edited()));
    }

    /** Asks the AI to look at this ticket again (for example after it was switched back on). */
    @Transactional
    public void requestRetriage(Long id) {
        Ticket t = find(id);
        if (t.getStatus().isFinished()) {
            throw new BadRequestException("Finished tickets are not triaged again");
        }
        events.publishEvent(new TicketCreated(t.getId()));
    }

    /** Used by the AI handler: the text to triage, or empty when the ticket should be left alone. */
    @Transactional(readOnly = true)
    public Optional<String[]> loadForTriage(Long id) {
        return tickets.findById(id)
                .filter(t -> !t.getStatus().isFinished())
                .map(t -> new String[]{t.getTitle(), t.getDescription()});
    }

    /**
     * Stores what the AI produced. The AI's suggestions (summary, similar tickets, reply) are always saved,
     * but it may only set category, priority and status when no human has triaged the ticket yet.
     */
    @Transactional
    public void applyAiTriage(Long id, PipelineResult r) {
        Ticket t = tickets.findById(id).orElse(null);
        if (t == null || t.getStatus().isFinished()) {
            return;
        }
        boolean aiProducedAnswer = r.confidence() > 0.0;
        t.setAiSummary(r.summary());
        t.setAiConfidence(r.confidence());
        t.setAiRationale(truncate(r.rationale(), 400));
        t.setAiNeedsHuman(r.needsHuman());
        t.setAiReason(truncate(r.reason(), 300));
        t.setAiSuggestedReply(r.suggestedReply());
        t.setAiSimilarJson(toJson(r.similar().stream().map(s -> mapOf(
                "ticketId", s.ticketId(), "title", s.title(), "score", s.score())).toList()));
        t.setAiSourcesJson(toJson(r.sources().stream().map(s -> mapOf(
                "type", s.type(), "id", s.id(), "title", s.title(), "score", s.score())).toList()));
        t.setAiProvider(r.provider());
        t.setAiModel(r.model());

        boolean humanAlreadyTriaged = t.getTriageSource() == TriageSource.MANUAL;
        if (!humanAlreadyTriaged && t.getStatus().isOpenForTriage()) {
            if (aiProducedAnswer) {
                t.setCategory(r.category());
                t.setPriority(r.priority());
                updateResolutionTarget(t, SeverityBand.forPriority(r.priority()));
                t.setTriageSource(TriageSource.AI);
            }
            t.setStatus(r.needsHuman() ? TicketStatus.NEEDS_HUMAN_TRIAGE : TicketStatus.TRIAGED);
        }
        t.touch();
    }

    /** Tickets that never got an AI answer, for the retry sweeper. */
    @Transactional(readOnly = true)
    public List<Long> staleNewTicketIds(Instant olderThan) {
        return tickets.findTop20ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(TicketStatus.NEW, olderThan)
                .stream().map(Ticket::getId).toList();
    }

    // ================================================================ helpers

    private Ticket find(Long id) {
        return tickets.findById(id).orElseThrow(() -> new NotFoundException("Ticket not found"));
    }

    /** Users can follow their own incidents through resolution but cannot browse another user's. */
    private Ticket visibleTicket(AuthUser caller, Long id) {
        Ticket t = find(id);
        if (!caller.isStaff() && !t.getRequester().getId().equals(caller.id())) {
            throw new NotFoundException("Ticket not found");
        }
        return t;
    }

    private static void updateResolutionTarget(Ticket ticket, SeverityBand band) {
        int days = band.defaultDays();
        ticket.setSeverityBand(band);
        ticket.setExpectedResolutionDays(days);
        ticket.setResolutionDueAt(Instant.now().plusSeconds(days * 86_400L));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static String toJson(Object o) {
        return Json.write(o);
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return m;
    }

}
