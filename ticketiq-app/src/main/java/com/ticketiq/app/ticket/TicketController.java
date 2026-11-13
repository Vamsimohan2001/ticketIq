package com.ticketiq.app.ticket;

import com.ticketiq.app.security.CurrentUser;
import com.ticketiq.app.ticket.TicketDtos.AssignRequest;
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
import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetail create(@Valid @RequestBody CreateTicketRequest request) {
        return service.create(CurrentUser.get(), request);
    }

    @GetMapping
    public PageResponse<TicketSummary> list(@RequestParam(required = false) TicketStatus status,
                                            @RequestParam(required = false) Category category,
                                            @RequestParam(required = false) Priority priority,
                                            @RequestParam(required = false, name = "q") String text,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.get(), status, category, priority, text, page, size);
    }

    @GetMapping("/{id}")
    public TicketDetail get(@PathVariable Long id) {
        return service.get(CurrentUser.get(), id);
    }

    @PatchMapping("/{id}/triage")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public TicketDetail triage(@PathVariable Long id, @Valid @RequestBody TriageRequest request) {
        return service.manualTriage(CurrentUser.get(), id, request);
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public TicketDetail assign(@PathVariable Long id, @RequestBody AssignRequest request) {
        return service.assign(CurrentUser.get(), id, request.assigneeId());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public TicketDetail status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(CurrentUser.get(), id, request);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        return service.addComment(CurrentUser.get(), id, request);
    }

    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(@PathVariable Long id) {
        return service.comments(CurrentUser.get(), id);
    }

    @GetMapping("/{id}/similar")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public List<SimilarView> similar(@PathVariable Long id) {
        return service.similar(id);
    }

    @GetMapping("/{id}/suggested-reply")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public SuggestedReplyView suggestedReply(@PathVariable Long id) {
        return service.suggestedReply(id);
    }

    @PostMapping("/{id}/ai-feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public void feedback(@PathVariable Long id, @RequestBody FeedbackRequest request) {
        service.recordFeedback(CurrentUser.get(), id, request);
    }

    @PostMapping("/{id}/retriage")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public void retriage(@PathVariable Long id) {
        service.requestRetriage(id);
    }
}
