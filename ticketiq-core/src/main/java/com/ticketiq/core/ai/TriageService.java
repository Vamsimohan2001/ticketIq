package com.ticketiq.core.ai;

import com.ticketiq.core.json.Json;
import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;
import java.util.Map;
import java.util.Optional;

/**
 * Asks the model to classify a ticket and turns the answer into a validated {@link TriageResult}
 * (integration point IP-3, structured output).
 *
 * <p>If the answer is not valid we make ONE repair attempt, telling the model what was wrong.
 * If that fails too we return empty and the guardrail sends the ticket to a human.
 */
public final class TriageService {

    private final AiGateway gateway;

    public TriageService(AiGateway gateway) {
        this.gateway = gateway;
    }

    /** @param redactedText title and description with personal data already removed */
    public Optional<TriageResult> triage(String ticketId, String redactedText) {
        String system = PromptTemplates.load("triage-system.txt");
        String user = PromptTemplates.render("triage-user.txt",
                Map.of("ticket", PromptTemplates.neutralise(redactedText)));

        String problem;
        try {
            String raw = gateway.chat(AiCallRecord.Kind.TRIAGE, ticketId, system, user, true);
            try {
                return Optional.of(parse(raw));
            } catch (RuntimeException invalid) {
                problem = invalid.getMessage();
            }
            // One repair attempt: show the model its own answer and what was wrong with it.
            String repair = user + "\n\nYour previous answer was invalid (" + problem
                    + "). Answer again with ONLY the JSON object and the exact keys and allowed values listed above.";
            String second = gateway.chat(AiCallRecord.Kind.TRIAGE, ticketId, system, repair, true);
            return Optional.of(parse(second));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static TriageResult parse(String raw) {
        Map<String, Object> json = Json.parseObject(Json.extractObject(raw));
        Category category = Category.parse(Json.string(json, "category"));
        Priority priority = Priority.parse(Json.string(json, "priority"));
        if (category == null) {
            throw new IllegalArgumentException("category is missing or not one of the allowed values");
        }
        if (priority == null) {
            throw new IllegalArgumentException("priority is missing or not one of the allowed values");
        }
        Double confidence = Json.number(json, "confidence");
        if (confidence == null) {
            throw new IllegalArgumentException("confidence is missing");
        }
        return new TriageResult(category, priority, Json.string(json, "summary"), confidence,
                Json.string(json, "rationale"));
    }
}
