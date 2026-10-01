package com.ryanpurakal.pariscompass.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** One metric for 2 to 4 countries, in the order they were requested. */
public record ComparisonResponse(String metric, String unit, @Schema(nullable = true) Integer from, @Schema(nullable = true) Integer to, List<CountrySeries> countries) {

    public record CountrySeries(String iso3, String name, List<SeriesPoint> points) {
    }
}
