package com.ticketiq.core.llm;

import com.ticketiq.core.json.Json;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A local model served by Ollama (POST /api/chat). Free, private, and runs offline. */
public final class OllamaChatModel implements ChatModel {

    private final HttpJson http;
    private final String baseUrl;
    private final String model;

    public OllamaChatModel(String baseUrl, String model, Duration timeout) {
        this.http = new HttpJson(timeout);
        this.baseUrl = GeminiChatModel.stripSlash(baseUrl);
        this.model = model;
    }

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String generate(String system, String user, boolean jsonOnly) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        if (jsonOnly) {
            body.put("format", "json");
        }
        body.put("options", Map.of("temperature", 0.2));
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        Map<String, Object> response = http.post(baseUrl + "/api/chat", Map.of(), body);
        String content = Json.string(Json.asMap(response.get("message")), "content");
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Ollama returned an empty answer");
        }
        return content;
    }
}
