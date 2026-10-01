package com.ryanpurakal.pariscompass.config;

import com.google.genai.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Creates the Gemini Client bean only when an API key is configured. Without a key the bean
 * is absent and GeminiService answers 503, so dev and test run without credentials.
 * Prod refuses to start without a key (see AppProperties.Gemini#required).
 */
@Slf4j
@Configuration
public class GeminiConfig {

    @Bean
    @Conditional(ApiKeyPresent.class)
    public Client geminiClient(AppProperties properties) {
        log.info("Gemini client enabled (model: {})", properties.gemini().model());
        return Client.builder().apiKey(properties.gemini().apiKey()).build();
    }

    static class ApiKeyPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("app.gemini.api-key"));
        }
    }
}
