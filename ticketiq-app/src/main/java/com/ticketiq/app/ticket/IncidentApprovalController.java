package com.ticketiq.app.ticket;

import com.ticketiq.app.ticket.IncidentRequestService.AdminDecision;
import com.ticketiq.app.ticket.IncidentRequestService.RequestView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
@PreAuthorize("hasRole('ADMIN')")
public class IncidentApprovalController {

    private final IncidentRequestService requests;

    public IncidentApprovalController(IncidentRequestService requests) {
        this.requests = requests;
    }

    @GetMapping
    public List<RequestView> pending() {
        return requests.pendingAdmin();
    }

    @PatchMapping("/{id}")
    public RequestView decide(@PathVariable long id, @Valid @RequestBody AdminDecision decision) {
        return requests.adminDecision(id, decision);
    }
}
