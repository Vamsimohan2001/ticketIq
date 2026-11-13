package com.ticketiq.core.llm;

import com.ticketiq.core.json.Json;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Google Gemini through its public REST API (generateContent). */
public final class GeminiChatModel implements ChatModel {

    private final HttpJson http;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public GeminiChatModel(String baseUrl, String apiKey, String model, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("GEMINI_API_KEY is required for the gemini provider");
        }
        this.http = new HttpJson(timeout);
        this.baseUrl = stripSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String provider() {
        return "gemini";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String generate(String system, String user, boolean jsonOnly) {
        Map<String, Object> generation = jsonOnly
                ? Map.of("temperature", 0.2, "responseMimeType", "application/json")
                : Map.of("temperature", 0.3);
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", user)))),
                "generationConfig", generation);
        Map<String, Object> response = http.post(
                baseUrl + "/v1beta/models/" + model + ":generateContent",
                Map.of("x-goog-api-key", apiKey), body);

        List<Object> candidates = Json.asList(response.get("candidates"));
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates");
        }
        Map<String, Object> content = Json.asMap(Json.asMap(candidates.get(0)).get("content"));
        List<Object> parts = Json.asList(content.get("parts"));
        StringBuilder text = new StringBuilder();
        for (Object part : parts) {
            String t = Json.string(Json.asMap(part), "text");
            if (t != null) {
                text.append(t);
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("Gemini returned an empty answer");
        }
        return text.toString();
    }

    static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
