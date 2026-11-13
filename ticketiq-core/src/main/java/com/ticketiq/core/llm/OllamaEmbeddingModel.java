package com.ticketiq.core.llm;

import com.ticketiq.core.json.Json;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Local embeddings through Ollama (POST /api/embed). */
public final class OllamaEmbeddingModel implements EmbeddingModel {

    private final HttpJson http;
    private final String baseUrl;
    private final String model;
    private final int dimensions;

    /** @param dimensions the size the model returns (nomic-embed-text gives 768) */
    public OllamaEmbeddingModel(String baseUrl, String model, int dimensions, Duration timeout) {
        this.http = new HttpJson(timeout);
        this.baseUrl = GeminiChatModel.stripSlash(baseUrl);
        this.model = model;
        this.dimensions = dimensions;
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
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(String text) {
        Map<String, Object> response = http.post(baseUrl + "/api/embed", Map.of(),
                Map.of("model", model, "input", text));
        List<Object> embeddings = Json.asList(response.get("embeddings"));
        if (embeddings.isEmpty()) {
            throw new IllegalStateException("Ollama returned no embeddings");
        }
        List<Object> values = Json.asList(embeddings.get(0));
        float[] v = new float[values.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = ((Number) values.get(i)).floatValue();
        }
        return Vectors.normalise(v);
    }
}
