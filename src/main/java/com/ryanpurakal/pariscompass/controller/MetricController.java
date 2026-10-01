package com.ryanpurakal.pariscompass.controller;

import com.ryanpurakal.pariscompass.model.ComparisonResponse;
import com.ryanpurakal.pariscompass.model.MetricInfo;
import com.ryanpurakal.pariscompass.model.RankingResponse;
import com.ryanpurakal.pariscompass.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Cross-country views: the metric catalog, rankings for a year, and side-by-side comparison. */
@Tag(name = "Metrics", description = "Metric catalog, rankings and multi-country comparison")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MetricController {
    private final AnalyticsService analytics;

    @Operation(summary = "Metric catalog with units, sources and data coverage")
    @GetMapping("/metrics")
    public List<MetricInfo> metrics() {
        return analytics.metricCatalog();
    }

    @Operation(summary = "Countries ranked by one metric in one year",
            description = "year defaults to the latest year with at least 90% of the metric's best coverage. Ties share a rank.")
    @ApiResponse(responseCode = "400", description = "UNKNOWN_METRIC, INVALID_ORDER, or a parameter out of range", useReturnTypeSchema = false)
    @GetMapping("/rankings")
    public RankingResponse rankings(
            @Parameter(example = "co2_per_capita_t") @RequestParam String metric,
            @RequestParam(required = false) @Min(1750) @Max(2100) Integer year,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "50") @Min(1) @Max(300) int limit) {
        return analytics.rankings(metric, year, order, limit);
    }

    @Operation(summary = "One metric for 2 to 4 countries, side by side")
    @ApiResponse(responseCode = "400", description = "UNKNOWN_METRIC, INVALID_COUNTRY_LIST, INVALID_YEAR_RANGE", useReturnTypeSchema = false)
    @ApiResponse(responseCode = "404", description = "COUNTRY_NOT_FOUND", useReturnTypeSchema = false)
    @GetMapping("/compare")
    public ComparisonResponse compare(
            @Parameter(description = "2 to 4 comma-separated ISO3 codes", example = "USA,CHN,IND")
            @RequestParam List<String> countries,
            @RequestParam String metric,
            @RequestParam(required = false) @Min(1750) @Max(2100) Integer from,
            @RequestParam(required = false) @Min(1750) @Max(2100) Integer to) {
        return analytics.compare(countries, metric, from, to);
    }
}
