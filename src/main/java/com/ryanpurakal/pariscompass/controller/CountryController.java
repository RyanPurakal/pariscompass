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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Countries", description = "Per-country metrics, time series, alignment score and projections")
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

    @Operation(summary = "List every country with ingested data, sorted by name")
    @ApiResponse(responseCode = "200", description = "Countries", useReturnTypeSchema = true)
    @GetMapping("/countries")
    public List<CountryInfo> getAllCountries() {
        return metricsService.getAllCountries();
    }

    @Operation(summary = "Latest value of each headline metric, with the year of each value")
    @ApiResponse(responseCode = "200", description = "Metrics snapshot", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "iso3 is not three letters")
    @ApiResponse(responseCode = "404", description = "COUNTRY_NOT_FOUND")
    @GetMapping("/countries/{iso3}")
    public CountryMetrics getCountryMetrics(
            @PathVariable @Pattern(regexp = ISO3_REGEX, message = ISO3_MESSAGE) String iso3) {
        return metricsService.getLatestMetrics(iso3.toUpperCase(Locale.ROOT));
    }

    /** Time series for one country. {@code metrics} defaults to every metric; years default to all available. */
    @Operation(summary = "Time series for one country over a year range")
    @ApiResponse(responseCode = "200", description = "One series per requested metric; years without data are absent", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "UNKNOWN_METRIC, INVALID_YEAR_RANGE, or a parameter out of range")
    @ApiResponse(responseCode = "404", description = "COUNTRY_NOT_FOUND")
    @GetMapping("/countries/{iso3}/series")
    public CountrySeriesResponse getCountrySeries(
            @PathVariable @Pattern(regexp = ISO3_REGEX, message = ISO3_MESSAGE) String iso3,
            @Parameter(description = "Comma-separated metric codes from GET /api/metrics; default all")
            @RequestParam(required = false) List<String> metrics,
            @RequestParam(required = false) @Min(1750) @Max(2100) Integer from,
            @RequestParam(required = false) @Min(1750) @Max(2100) Integer to) {
        return analytics.countrySeries(iso3.toUpperCase(Locale.ROOT), metrics, from, to);
    }

    /** Deterministic Paris alignment score (formula in SCORING.md). Computed in Java, never by the LLM. */
    @Operation(summary = "Deterministic Paris alignment score (formula v1, see SCORING.md)",
            description = "Computed in Java from historical data. score and band are null when the emissions trend "
                    + "cannot be computed; reason explains why.")
    @ApiResponse(responseCode = "200", description = "Score with every component's input, sub-score and weight", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "COUNTRY_NOT_FOUND")
    @GetMapping("/countries/{iso3}/alignment")
    public AlignmentResponse getAlignment(
            @PathVariable @Pattern(regexp = ISO3_REGEX, message = ISO3_MESSAGE) String iso3) {
        return alignment.score(iso3.toUpperCase(Locale.ROOT));
    }

    /**
     * Five-year CO2 projection. POST because it may call a paid external model and creates a stored record.
     * Falls back to labeled trend extrapolation if the model is unavailable or its output fails validation.
     */
    @Operation(summary = "Five-year CO2 projection (rate limited)",
            description = "Returns a stored projection when the inputs are unchanged (cached=true). Otherwise asks the "
                    + "model for schema-validated JSON, retries once, and falls back to labeled trend extrapolation.")
    @ApiResponse(responseCode = "200", description = "Metrics snapshot plus projection", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "COUNTRY_NOT_FOUND")
    @ApiResponse(responseCode = "422", description = "INSUFFICIENT_DATA: fewer than 6 of the last 10 years of CO2 data")
    @ApiResponse(responseCode = "429", description = "RATE_LIMITED, with a Retry-After header")
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
    @Operation(summary = "Stored projections for a country, newest first")
    @ApiResponse(responseCode = "200", description = "Stored projections", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "COUNTRY_NOT_FOUND")
    @GetMapping("/countries/{iso3}/projections")
    public List<ProjectionResponse> getProjectionHistory(
            @PathVariable @Pattern(regexp = ISO3_REGEX, message = ISO3_MESSAGE) String iso3,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return projectionService.history(iso3.toUpperCase(Locale.ROOT), limit);
    }
}
