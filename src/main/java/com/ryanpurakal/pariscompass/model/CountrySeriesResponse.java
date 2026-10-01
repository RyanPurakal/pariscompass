package com.ryanpurakal.pariscompass.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CountrySeriesResponse(String iso3, String name, @Schema(nullable = true) Integer from, @Schema(nullable = true) Integer to, List<MetricSeries> series) {
}
