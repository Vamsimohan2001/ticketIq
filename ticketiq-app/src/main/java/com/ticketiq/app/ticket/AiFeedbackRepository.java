package com.ticketiq.app.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long> {

    long countByAccepted(boolean accepted);
}
