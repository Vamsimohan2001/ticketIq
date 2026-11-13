package com.ticketiq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.ticketiq.core.json.Json;
import com.ticketiq.core.llm.GeminiChatModel;
import com.ticketiq.core.llm.GeminiEmbeddingModel;
import com.ticketiq.core.llm.NonRetryableAiException;
import com.ticketiq.core.llm.OllamaChatModel;
import com.ticketiq.core.llm.OllamaEmbeddingModel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The real Gemini and Ollama clients talk to a tiny local fake server, so we verify the exact
 * requests they send and how they read the replies, without internet access or an API key.
 */
class HttpModelClientsTest {

    private record Fake(HttpServer server, AtomicReference<String> lastBody, AtomicReference<String> lastPath,
                        AtomicReference<String> lastKeyHeader) {
        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }
    }

    private static Fake fake(int status, String response) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        server.createContext("/", ex -> {
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            path.set(ex.getRequestURI().getPath());
            key.set(ex.getRequestHeaders().getFirst("x-goog-api-key"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        return new Fake(server, body, path, key);
    }

    @Test
    void geminiChatSendsTheRightRequestAndReadsTheAnswer() throws Exception {
        Fake f = fake(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"ok\\\":true}\"}]}}]}");
        try {
            GeminiChatModel model = new GeminiChatModel(f.url(), "secret-key", "gemini-test", Duration.ofSeconds(5));
            String answer = model.generate("be brief", "classify this", true);
            assertEquals("{\"ok\":true}", answer);
            assertEquals("/v1beta/models/gemini-test:generateContent", f.lastPath().get());
            assertEquals("secret-key", f.lastKeyHeader().get(), "API key goes in a header, never the URL");
            Map<String, Object> sent = Json.parseObject(f.lastBody().get());
            assertEquals("be brief", Json.string(Json.asMap(Json.asMap(Json.asList(
                    Json.asMap(sent.get("systemInstruction")).get("parts")).get(0))), "text"));
            assertEquals("application/json", Json.string(Json.asMap(sent.get("generationConfig")), "responseMimeType"));
        } finally {
            f.stop();
        }
    }

    @Test
    void geminiEmbeddingRequestsTheConfiguredSizeAndNormalises() throws Exception {
        Fake f = fake(200, "{\"embedding\":{\"values\":[3.0,4.0]}}");
        try {
            GeminiEmbeddingModel model = new GeminiEmbeddingModel(f.url(), "k", "gemini-embedding-test", 2, Duration.ofSeconds(5));
            float[] v = model.embed("hello");
            assertEquals(0.6f, v[0], 1e-5);
            assertEquals(0.8f, v[1], 1e-5);
            assertEquals("/v1beta/models/gemini-embedding-test:embedContent", f.lastPath().get());
            Map<String, Object> sent = Json.parseObject(f.lastBody().get());
            assertEquals(2L, sent.get("outputDimensionality"));
        } finally {
            f.stop();
        }
    }

    @Test
    void ollamaChatAndEmbeddingWork() throws Exception {
        Fake chat = fake(200, "{\"message\":{\"role\":\"assistant\",\"content\":\"hello there\"}}");
        Fake emb = fake(200, "{\"embeddings\":[[0.0,2.0]]}");
        try {
            OllamaChatModel model = new OllamaChatModel(chat.url(), "llama-test", Duration.ofSeconds(5));
            assertEquals("hello there", model.generate("s", "u", false));
            assertEquals("/api/chat", chat.lastPath().get());
            Map<String, Object> sent = Json.parseObject(chat.lastBody().get());
            assertEquals(Boolean.FALSE, sent.get("stream"));

            OllamaEmbeddingModel e = new OllamaEmbeddingModel(emb.url(), "nomic", 2, Duration.ofSeconds(5));
            float[] v = e.embed("text");
            assertEquals(0f, v[0], 1e-6);
            assertEquals(1f, v[1], 1e-6);
            assertEquals("/api/embed", emb.lastPath().get());
        } finally {
            chat.stop();
            emb.stop();
        }
    }

    @Test
    void clientErrorsAreNotRetryableButServerErrorsAre() throws Exception {
        Fake bad = fake(401, "{\"error\":\"bad key\"}");
        Fake down = fake(503, "{\"error\":\"overloaded\"}");
        try {
            GeminiChatModel unauthorised = new GeminiChatModel(bad.url(), "k", "m", Duration.ofSeconds(5));
            assertThrows(NonRetryableAiException.class, () -> unauthorised.generate("s", "u", false));
            GeminiChatModel overloaded = new GeminiChatModel(down.url(), "k", "m", Duration.ofSeconds(5));
            RuntimeException e = assertThrows(RuntimeException.class, () -> overloaded.generate("s", "u", false));
            assertTrue(!(e instanceof NonRetryableAiException), "503 should be retryable");
        } finally {
            bad.stop();
            down.stop();
        }
    }

    @Test
    void geminiRequiresAnApiKey() {
        assertThrows(IllegalArgumentException.class, () -> new GeminiChatModel("http://x", " ", "m", Duration.ofSeconds(1)));
    }
}
