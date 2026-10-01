package com.ryanpurakal.pariscompass.controller;

import com.ryanpurakal.pariscompass.model.ComparisonResponse;
import com.ryanpurakal.pariscompass.model.MetricInfo;
import com.ryanpurakal.pariscompass.model.RankingResponse;
import com.ryanpurakal.pariscompass.service.AnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Cross-country views: the metric catalog, rankings for a year, and side-by-side comparison. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MetricController {
    private final AnalyticsService analytics;

    @GetMapping("/metrics")
    public List<MetricInfo> metrics() {
        return analytics.metricCatalog();
    }

    @GetMapping("/rankings")
    public RankingResponse rankings(
            @RequestParam String metric,
            @RequestParam(required = false) @Min(1750) @Max(2100) Integer year,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "50") @Min(1) @Max(300) int limit) {
        return analytics.rankings(metric, year, order, limit);
    }

    @GetMapping("/compare")
    public ComparisonResponse compare(
            @RequestParam List<String> countries,
            @RequestParam String metric,
            @RequestParam(required = false) @Min(1750) @Max(2100) Integer from,
            @RequestParam(required = false) @Min(1750) @Max(2100) Integer to) {
        return analytics.compare(countries, metric, from, to);
    }
}
