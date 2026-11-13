package com.ticketiq.core.ai;

import java.util.function.Function;

/**
 * Every tunable in one place. The Spring app fills this from application.yml;
 * the command-line evaluation runner fills it from environment variables.
 */
public record AiSettings(
        Provider provider,
        int dimensions,
        double confidenceThreshold,
        double similarityThreshold,
        double replyContextThreshold,
        int timeoutSeconds,
        int maxRetries,
        int rateLimitPerMinute,
        int cacheTtlSeconds,
        String geminiApiKey,
        String geminiBaseUrl,
        String geminiChatModel,
        String geminiEmbeddingModel,
        String ollamaBaseUrl,
        String ollamaChatModel,
        String ollamaEmbeddingModel) {

    public enum Provider {
        MOCK, GEMINI, OLLAMA;

        public static Provider parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return MOCK;
            }
            String key = raw.trim().toLowerCase();
            if (key.startsWith("ai-")) {
                key = key.substring(3);
            }
            return switch (key) {
                case "mock" -> MOCK;
                case "gemini" -> GEMINI;
                case "ollama" -> OLLAMA;
                default -> throw new IllegalArgumentException("Unknown AI provider '" + raw + "'. Use mock, gemini or ollama.");
            };
        }
    }

    /** Safe defaults: mock provider, no network, no key. */
    public static AiSettings defaults() {
        return fromLookup(key -> null);
    }

    /** Reads the AI_* and GEMINI_* / OLLAMA_* environment variables, falling back to defaults. */
    public static AiSettings fromEnv() {
        return fromLookup(System::getenv);
    }

    public static AiSettings fromLookup(Function<String, String> env) {
        Provider provider = Provider.parse(env.apply("AI_PROVIDER"));
        // Real embeddings score closer to 1.0 than a hashed bag of words, so the cut-off differs.
        double defaultSimilarity = provider == Provider.MOCK ? 0.5 : provider == Provider.OLLAMA ? 0.7 : 0.75;
        return new AiSettings(
                provider,
                intValue(env.apply("EMBEDDING_DIMENSIONS"), 768),
                doubleValue(env.apply("AI_CONFIDENCE_THRESHOLD"), 0.6),
                doubleValue(env.apply("AI_SIMILARITY_THRESHOLD"), defaultSimilarity),
                doubleValue(env.apply("AI_REPLY_CONTEXT_THRESHOLD"), provider == Provider.MOCK ? 0.2 : 0.5),
                intValue(env.apply("AI_TIMEOUT_SECONDS"), 15),
                intValue(env.apply("AI_MAX_RETRIES"), 2),
                intValue(env.apply("AI_RATE_LIMIT_PER_MIN"), 30),
                intValue(env.apply("AI_CACHE_TTL_SECONDS"), 3600),
                orDefault(env.apply("GEMINI_API_KEY"), ""),
                orDefault(env.apply("GEMINI_BASE_URL"), "https://generativelanguage.googleapis.com"),
                orDefault(env.apply("GEMINI_CHAT_MODEL"), "gemini-2.5-flash"),
                orDefault(env.apply("GEMINI_EMBEDDING_MODEL"), "gemini-embedding-001"),
                orDefault(env.apply("OLLAMA_BASE_URL"), "http://localhost:11434"),
                orDefault(env.apply("OLLAMA_CHAT_MODEL"), "llama3.2"),
                orDefault(env.apply("OLLAMA_EMBEDDING_MODEL"), "nomic-embed-text"));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int intValue(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double doubleValue(String value, double fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
