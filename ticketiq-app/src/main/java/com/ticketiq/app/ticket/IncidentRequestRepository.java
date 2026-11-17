package com.ticketiq.app.ticket;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRequestRepository extends JpaRepository<IncidentRequest, Long> {
    List<IncidentRequest> findAllByStatusOrderByCreatedAtAsc(IncidentRequest.Status status);
    List<IncidentRequest> findAllByTicketIdAndStatusOrderByCreatedAtAsc(Long ticketId, IncidentRequest.Status status);
}
