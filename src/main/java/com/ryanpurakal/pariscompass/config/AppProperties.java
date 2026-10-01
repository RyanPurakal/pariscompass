package com.ryanpurakal.pariscompass.config;

import com.ryanpurakal.pariscompass.etl.DataSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Typed, validated view of every {@code app.*} setting. Values come from application*.yml,
 * which in turn reads environment variables, so secrets never live in the repo.
 *
 * Validation runs at startup: a bad config stops the app before it serves traffic,
 * instead of failing on the first request that needs the value.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid @NotNull Cors cors, @Valid @NotNull Gemini gemini, @Valid Etl etl,
                            @Valid Projection projection, @Valid RateLimit rateLimit) {

    public AppProperties {
        if (etl == null) {
            etl = new Etl(false, false, false, Map.of());
        }
        if (projection == null) {
            projection = new Projection(null);
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(5, 100);
        }
    }

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

    /**
     * Ingestion job settings. {@code sources} maps each DataSource to an https://, file: or
     * classpath: location, so tests and offline runs can point at local files.
     * {@code force} re-ingests a source even when its file hash matches the last successful run.
     */
    public record Etl(boolean runOnStartup, boolean exitAfterRun, boolean force, Map<DataSource, String> sources) {
        public Etl {
            sources = sources == null ? Map.of() : Map.copyOf(sources);
        }
    }

    /**
     * Projection caching. The cache key already changes when data or prompt change, so the TTL only
     * bounds how long one model output is reused for unchanged inputs.
     */
    public record Projection(Duration cacheTtl) {
        public Projection {
            if (cacheTtl == null) {
                cacheTtl = Duration.ofDays(30);
            }
        }
    }

    /**
     * Limits on POST /api/countries/{iso3}/projection, the only endpoint that can call a paid model.
     * The per-client limit is keyed by client IP and is best effort; the global hourly cap is what
     * bounds model spend no matter how many IPs a caller uses.
     */
    public record RateLimit(@Positive int projectionsPerClientPerMinute, @Positive int projectionsPerHourGlobal) {
    }
}
