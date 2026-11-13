package com.ticketiq.app.ticket;

import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;
import org.springframework.data.jpa.domain.Specification;

/** Small building blocks for the ticket list filters. A null value means "no filter". */
final class TicketSpecs {

    private TicketSpecs() {
    }

    static Specification<Ticket> status(TicketStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    static Specification<Ticket> unfinished() {
        return (root, query, cb) -> cb.not(root.get("status").in(TicketStatus.RESOLVED, TicketStatus.CLOSED));
    }

    static Specification<Ticket> category(Category category) {
        return (root, query, cb) -> category == null ? null : cb.equal(root.get("category"), category);
    }

    static Specification<Ticket> priority(Priority priority) {
        return (root, query, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    static Specification<Ticket> requester(Long requesterId) {
        return (root, query, cb) -> requesterId == null ? null
                : cb.equal(root.get("requester").get("id"), requesterId);
    }

    static Specification<Ticket> titleContains(String text) {
        return (root, query, cb) -> text == null || text.isBlank() ? null
                : cb.like(cb.lower(root.get("title")), "%" + text.trim().toLowerCase() + "%");
    }
}
