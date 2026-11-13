package com.ticketiq.app.config;

import com.ticketiq.app.ai.AiAuditService;
import com.ticketiq.core.ai.AiRuntime;
import com.ticketiq.core.ai.AiSettings;
import com.ticketiq.core.ai.RateLimiter;
import com.ticketiq.core.redaction.PiiRedactor;
import com.ticketiq.core.security.JwtService;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Wires the framework-free core classes into Spring as beans. */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /** The one place where the chosen AI provider (mock, gemini, ollama) is turned into real objects. */
    @Bean(destroyMethod = "close")
    public AiRuntime aiRuntime(AppProperties props, AiAuditService audit) {
        AppProperties.Ai ai = props.ai();
        AiSettings base = AiSettings.fromLookup(key -> "AI_PROVIDER".equals(key) ? ai.provider() : null);
        AiSettings settings = new AiSettings(
                base.provider(), ai.dimensions(), ai.confidenceThreshold(),
                ai.similarityThreshold() >= 0 ? ai.similarityThreshold() : base.similarityThreshold(),
                ai.replyContextThreshold() >= 0 ? ai.replyContextThreshold() : base.replyContextThreshold(),
                ai.timeoutSeconds(), ai.maxRetries(), ai.rateLimitPerMinute(), ai.cacheTtlSeconds(),
                ai.geminiApiKey(), ai.geminiBaseUrl(), ai.geminiChatModel(), ai.geminiEmbeddingModel(),
                ai.ollamaBaseUrl(), ai.ollamaChatModel(), ai.ollamaEmbeddingModel());

        AiRuntime runtime = AiRuntime.create(settings);
        runtime.gateway().addListener(audit);
        log.info("AI provider: {} (chat model {}, embeddings {})", runtime.gateway().provider(),
                runtime.gateway().model(), runtime.gateway().embeddingModel());
        return runtime;
    }

    /** Lets an admin switch the AI off at runtime to see tickets keep working without it. */
    @Bean
    public AtomicBoolean aiSwitch(AppProperties props) {
        return new AtomicBoolean(props.ai().enabled());
    }

    @Bean
    public PiiRedactor piiRedactor(AiRuntime runtime) {
        return runtime.redactor();
    }

    @Bean
    public JwtService jwtService(AppProperties props) {
        return new JwtService(props.jwt().secret());
    }

    /** Five wrong passwords in five minutes locks the login for that user ID until the window passes. */
    @Bean
    public RateLimiter loginFailureLimiter() {
        return new RateLimiter(5, Duration.ofMinutes(5));
    }

    /** Background threads for AI work, so creating a ticket never waits for a model. */
    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-");
        executor.initialize();
        return executor;
    }
}
