package com.ticketiq.app.kb;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbRepository extends JpaRepository<KbArticle, Long> {

    List<KbArticle> findAllByOrderByTitleAsc();
}
