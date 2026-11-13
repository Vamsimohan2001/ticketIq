package com.ticketiq.app.ai;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCallRepository extends JpaRepository<AiCall, Long> {

    List<AiCall> findTop20ByOrderByIdDesc();
}
