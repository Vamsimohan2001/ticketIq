package com.ticketiq.core.llm;

import com.ticketiq.core.json.Json;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Gemini embeddings (embedContent). Output size is requested explicitly, then normalised. */
public final class GeminiEmbeddingModel implements EmbeddingModel {

    private final HttpJson http;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int dimensions;

    public GeminiEmbeddingModel(String baseUrl, String apiKey, String model, int dimensions, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("GEMINI_API_KEY is required for the gemini provider");
        }
        this.http = new HttpJson(timeout);
        this.baseUrl = GeminiChatModel.stripSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
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
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(String text) {
        Map<String, Object> body = Map.of(
                "model", "models/" + model,
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "outputDimensionality", dimensions);
        Map<String, Object> response = http.post(
                baseUrl + "/v1beta/models/" + model + ":embedContent",
                Map.of("x-goog-api-key", apiKey), body);
        List<Object> values = Json.asList(Json.asMap(response.get("embedding")).get("values"));
        if (values.isEmpty()) {
            throw new IllegalStateException("Gemini returned an empty embedding");
        }
        float[] v = new float[values.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = ((Number) values.get(i)).floatValue();
        }
        return Vectors.normalise(v);
    }
}
