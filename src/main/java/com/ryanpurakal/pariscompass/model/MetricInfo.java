package com.ryanpurakal.pariscompass.model;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * A metric and how much data exists for it. {@code defaultYear} is the latest year with at least
 * 90% of the metric's best per-year coverage: the newest year can be partly reported
 * (e.g. 2025 electricity data covers 90 of 212 countries), and ranking it would mislead.
 */
public record MetricInfo(
        String code,
        String name,
        String unit,
        String description,
        Source source,
        Coverage coverage) {

    public record Source(String code, String name, String homepage, String citation) {
    }

    public record Coverage(long values, int countries, @Schema(nullable = true) Integer firstYear, @Schema(nullable = true) Integer lastYear, @Schema(nullable = true) Integer defaultYear) {
    }
}
