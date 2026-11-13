package com.ticketiq.app.config;

import com.ticketiq.app.ticket.TicketTeams;
import com.ticketiq.core.ai.AiRuntime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public info the sign-in page shows, such as which AI provider is active. Contains no secrets. */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final AiRuntime ai;
    private final AtomicBoolean aiSwitch;

    public MetaController(AiRuntime ai, AtomicBoolean aiSwitch) {
        this.ai = ai;
        this.aiSwitch = aiSwitch;
    }

    @GetMapping
    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "TicketIQ");
        m.put("version", "2.0.0");
        m.put("aiProvider", ai.gateway().provider());
        m.put("aiModel", ai.gateway().model());
        m.put("aiEnabled", aiSwitch.get());
        m.put("teams", TicketTeams.asMap());
        return m;
    }
}
