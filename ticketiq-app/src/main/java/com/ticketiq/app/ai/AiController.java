package com.ticketiq.app.ai;

import com.ticketiq.core.ai.AiRuntime;
import com.ticketiq.core.ai.PipelineResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tools for trying and watching the AI without touching any real ticket. */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    public record DryRunRequest(@NotBlank @Size(max = 200) String title,
                                @NotBlank @Size(max = 4000) String description) {
    }

    public record SwitchRequest(boolean enabled) {
    }

    private final AiRuntime ai;
    private final AiCallRepository calls;
    private final AtomicBoolean aiSwitch;

    public AiController(AiRuntime ai, AiCallRepository calls, AtomicBoolean aiSwitch) {
        this.ai = ai;
        this.calls = calls;
        this.aiSwitch = aiSwitch;
    }

    /** Runs the full pipeline on the supplied text and returns the result. Nothing is saved. */
    @PostMapping("/dry-run/triage")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public PipelineResult dryRun(@Valid @RequestBody DryRunRequest request) {
        return ai.pipeline().run("dry-run", request.title(), request.description());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", aiSwitch.get());
        out.put("provider", ai.gateway().provider());
        out.put("chatModel", ai.gateway().model());
        out.put("embeddingModel", ai.gateway().embeddingModel());
        out.put("circuit", ai.gateway().executor().state().name());
        out.put("indexedDocuments", ai.vectorStore().size());
        out.put("confidenceThreshold", ai.settings().confidenceThreshold());
        out.put("similarityThreshold", ai.settings().similarityThreshold());
        out.put("metrics", ai.metrics().snapshot());
        out.put("recentCalls", calls.findTop20ByOrderByIdDesc().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ticketId", c.getTicketId());
            m.put("kind", c.getKind());
            m.put("provider", c.getProvider());
            m.put("latencyMs", c.getLatencyMs());
            m.put("success", c.isSuccess());
            m.put("error", c.getError());
            m.put("at", c.getCreatedAt());
            return m;
        }).toList());
        return out;
    }

    /** Demo switch: turn the AI off, create a ticket, turn it back on, and watch the ticket get triaged. */
    @PutMapping("/switch")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> toggle(@RequestBody SwitchRequest request) {
        aiSwitch.set(request.enabled());
        return Map.of("enabled", aiSwitch.get());
    }
}
