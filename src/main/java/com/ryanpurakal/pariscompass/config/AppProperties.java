package com.ryanpurakal.pariscompass.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Typed, validated view of every {@code app.*} setting. Values come from application*.yml,
 * which in turn reads environment variables, so secrets never live in the repo.
 *
 * Validation runs at startup: a bad config stops the app before it serves traffic,
 * instead of failing on the first request that needs the value.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid @NotNull Cors cors, @Valid @NotNull Gemini gemini) {

    /** Browser origins allowed to call /api. A wildcard is rejected so prod must name its frontend. */
    public record Cors(@NotEmpty(message = "set CORS_ALLOWED_ORIGINS") List<@NotBlank String> allowedOrigins) {

        @AssertTrue(message = "must list explicit origins, not '*'")
        public boolean isNoWildcard() {
            return allowedOrigins == null || !allowedOrigins.contains("*");
        }
    }

    /**
     * Gemini settings. When {@code required} is false (dev, test) a missing key disables
     * projections (503) but the rest of the API runs. When true (prod) a missing key fails startup.
     */
    public record Gemini(@NotBlank String model, String apiKey, boolean required) {

        @AssertTrue(message = "GEMINI_API_KEY must be set when app.gemini.required=true")
        public boolean isApiKeyPresentWhenRequired() {
            return !required || isConfigured();
        }

        public boolean isConfigured() {
            return StringUtils.hasText(apiKey);
        }

        /** Never print the key: records include every component in toString by default. */
        @Override
        public String toString() {
            return "Gemini[model=" + model + ", apiKey=" + (isConfigured() ? "****" : "<unset>")
                    + ", required=" + required + "]";
        }
    }
}
