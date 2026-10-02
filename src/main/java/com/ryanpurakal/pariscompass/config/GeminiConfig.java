package com.ryanpurakal.pariscompass.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.ryanpurakal.pariscompass.projection.GeminiProjectionModel;
import com.ryanpurakal.pariscompass.projection.ProjectionModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Creates the Gemini-backed ProjectionModel only when an API key is configured. Without a key there is
 * no model bean and ProjectionService serves the labeled statistical fallback, so dev and tests run
 * without credentials. Prod refuses to start without a key (see AppProperties.Gemini#required).
 */
@Slf4j
@Configuration
public class GeminiConfig {
    /** Per-request timeout for Gemini calls, in milliseconds. */
    static final int TIMEOUT_MS = 30_000;
    /**
     * One attempt per call. The SDK's default retried an HTTP 503 three times with backoff (measured in
     * GeminiProjectionModelTest), stacking with ProjectionService's own validation retry. A failed call
     * already becomes a labeled fallback that is not cached, so the next request simply tries again.
     * Worst case per projection: 2 attempts x 30 s, inside the 90 s that coalesced requests wait.
     */
    static final int SDK_ATTEMPTS = 1;

    /** HTTP settings for the Gemini client; exposed so tests can verify them against a local server. */
    public static HttpOptions httpOptions() {
        return HttpOptions.builder()
                .timeout(TIMEOUT_MS)
                .retryOptions(HttpRetryOptions.builder().attempts(SDK_ATTEMPTS).build())
                .build();
    }

    @Bean
    @Conditional(ApiKeyPresent.class)
    public ProjectionModel geminiProjectionModel(AppProperties properties) {
        log.info("Gemini projection model enabled (model: {})", properties.gemini().model());
        Client client = Client.builder()
                .apiKey(properties.gemini().apiKey())
                .httpOptions(httpOptions())
                .build();
        return new GeminiProjectionModel(client, properties.gemini().model());
    }

    static class ApiKeyPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("app.gemini.api-key"));
        }
    }
}
