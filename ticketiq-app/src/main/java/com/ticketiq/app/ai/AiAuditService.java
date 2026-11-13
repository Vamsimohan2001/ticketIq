package com.ticketiq.app.ai;

import com.ticketiq.core.ai.AiCallListener;
import com.ticketiq.core.ai.AiCallRecord;
import org.springframework.stereotype.Component;

/** Plugs into the AI gateway and stores an audit row after every model call. */
@Component
public class AiAuditService implements AiCallListener {

    private final AiCallRepository repo;

    public AiAuditService(AiCallRepository repo) {
        this.repo = repo;
    }

    @Override
    public void onCall(AiCallRecord r) {
        repo.save(new AiCall(parseId(r.ticketId()), r.kind().name(), r.provider(), r.model(), r.latencyMs(),
                r.success(), r.error(), r.at()));
    }

    private static Long parseId(String id) {
        try {
            return id == null ? null : Long.valueOf(id);
        } catch (NumberFormatException e) {
            return null;   // "dry-run" and similar labels have no ticket
        }
    }
}
