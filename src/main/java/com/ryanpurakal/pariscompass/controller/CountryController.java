package com.ryanpurakal.pariscompass.controller;

import com.ryanpurakal.pariscompass.model.AlignmentResponse;
import com.ryanpurakal.pariscompass.model.CountryInfo;
import com.ryanpurakal.pariscompass.model.CountryMetrics;
import com.ryanpurakal.pariscompass.model.CountryProjectionResponse;
import com.ryanpurakal.pariscompass.model.CountrySeriesResponse;
import com.ryanpurakal.pariscompass.model.ProjectionResponse;
import com.ryanpurakal.pariscompass.service.AlignmentService;
import com.ryanpurakal.pariscompass.service.AnalyticsService;
import com.ryanpurakal.pariscompass.service.CountryMetricsService;
import com.ryanpurakal.pariscompass.service.ProjectionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/**
 * HTTP boundary. Translates /api requests into service calls; contains no business logic.
 * ISO3 codes are validated (three letters, else 400) and uppercased before reaching services.
 * Validation uses Spring 6.1 built-in method validation (no class-level @Validated), so a bad
 * path variable raises HandlerMethodValidationException, which the handler renders as 400.
 * Errors are thrown as exceptions and rendered by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CountryController {
    private static final String ISO3_REGEX = "^[A-Za-z]{3}$";
    private static final String ISO3_MESSAGE = "must be a 3-letter ISO 3166-1 alpha-3 code";

    private final ProjectionService projectionService;
    private final CountryMetricsService metricsService;
    private final AnalyticsService analytics;
    private final AlignmentService alignment;

    @GetMapping("/countries")
    public List<CountryInfo> getAllCountries() {
        return metricsService.getAllCountries();
    }

    @GetMapping("/countries/{iso3}")
    public CountryMetrics getCountryMetrics(
            @PathVariable @Pattern(regexp = ISO3_REGEX, message = ISO3_MESSAGE) String iso3) {
        return metricsService.getLatestMetrics(iso3.toUpperCase(Locale.ROOT));
    }

    /** Time series for one country. {@code metrics} defaults to every metric; years default to all available. */
    @GetMapping("/countries/{iso3}/series")
    public CountrySeriesResponse getCountrySeries(
            @PathVariable @Pattern(regexp = ISO3_REGEX, message = ISO3_MESSAGE) String iso3,
            @RequestParam(required = false) List<String> metrics,
            @RequestParam(required = false) @Min(1750) @Max(2100) Integer from,
            @RequestParam(required = false) @Min(1750) @Max(2100) Integer to) {
        return analytics.countrySeries(iso3.toUpperCase(Locale.ROOT), metrics, from, to);
    }

    /** Deterministic Paris alignment score (formula in SCORING.md). Computed in Java, never by the LLM. */
    @GetMapping("/countries/{iso3}/alignment")
    public AlignmentResponse getAlignment(
            @PathVariable @Pattern(regexp = ISO3_REGEX, message = ISO3_MESSAGE) String iso3) {
        return alignment.score(iso3.toUpperCase(Locale.ROOT));
    }

    /**
     * Five-year CO2 projection. POST because it may call a paid external model and creates a stored record.
     * Falls back to labeled trend extrapolation if the model is unavailable or its output fails validation.
     */
    @PostMapping("/countries/{iso3}/projection")
    public CountryProjectionResponse getCountryProjection(
            @PathVariable @Pattern(regexp = ISO3_REGEX, message = ISO3_MESSAGE) String iso3) {
        String code = iso3.toUpperCase(Locale.ROOT);
        CountryMetrics metrics = metricsService.getLatestMetrics(code);
        return CountryProjectionResponse.builder()
                .metrics(metrics)
                .projection(projectionService.project(code))
                .build();
    }

    /** Stored projections for a country, newest first: cache entries, fallbacks, and older model or prompt versions. */
    @GetMapping("/countries/{iso3}/projections")
    public List<ProjectionResponse> getProjectionHistory(
            @PathVariable @Pattern(regexp = ISO3_REGEX, message = ISO3_MESSAGE) String iso3,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return projectionService.history(iso3.toUpperCase(Locale.ROOT), limit);
    }
}
