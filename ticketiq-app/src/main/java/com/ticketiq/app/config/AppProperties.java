package com.ticketiq.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code ticketiq.*} section of application.yml. */
@ConfigurationProperties(prefix = "ticketiq")
public record AppProperties(Jwt jwt, Ai ai, Seed seed) {

    public record Jwt(String secret, int accessMinutes, int refreshDays) {
    }

    /**
     * AI settings. A negative threshold means "use the default for the chosen provider".
     */
    public record Ai(boolean enabled, String provider, int dimensions, double confidenceThreshold,
                     double similarityThreshold, double replyContextThreshold, int timeoutSeconds,
                     int maxRetries, int rateLimitPerMinute, int cacheTtlSeconds, long sweepMs,
                     String geminiApiKey, String geminiBaseUrl, String geminiChatModel,
                     String geminiEmbeddingModel, String ollamaBaseUrl, String ollamaChatModel,
                     String ollamaEmbeddingModel) {
    }

    public record Seed(boolean enabled, String password) {
    }
}
