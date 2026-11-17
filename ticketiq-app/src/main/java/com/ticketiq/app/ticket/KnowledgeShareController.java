package com.ticketiq.app.ticket;

import com.ticketiq.app.security.CurrentUser;
import com.ticketiq.app.ticket.KnowledgeShareService.SharedIncident;
import com.ticketiq.app.ticket.KnowledgeShareService.ShareInput;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets/{id}")
public class KnowledgeShareController {

    private final KnowledgeShareService knowledge;

    public KnowledgeShareController(KnowledgeShareService knowledge) {
        this.knowledge = knowledge;
    }

    @PostMapping("/knowledge-access-request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestAccess(@PathVariable long id) {
        knowledge.requestAccess(CurrentUser.get(), id);
    }

    @GetMapping("/knowledge-recommendations")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SharedIncident> recommendations(@PathVariable long id) {
        return knowledge.recommendations(id);
    }

    @GetMapping("/related-incidents")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public List<SharedIncident> relatedIncidents(@PathVariable long id) {
        return knowledge.recommendations(id);
    }

    @PutMapping("/knowledge-share")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void share(@PathVariable long id, @Valid @RequestBody ShareInput input) {
        knowledge.share(CurrentUser.get(), id, input);
    }

    @GetMapping("/knowledge-share")
    public List<SharedIncident> shared(@PathVariable long id) {
        return knowledge.shared(CurrentUser.get(), id);
    }
}
