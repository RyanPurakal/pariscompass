package com.ryanpurakal.pariscompass.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
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

    @Bean
    @Conditional(ApiKeyPresent.class)
    public ProjectionModel geminiProjectionModel(AppProperties properties) {
        log.info("Gemini projection model enabled (model: {})", properties.gemini().model());
        Client client = Client.builder()
                .apiKey(properties.gemini().apiKey())
                .httpOptions(HttpOptions.builder().timeout(TIMEOUT_MS).build())
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
