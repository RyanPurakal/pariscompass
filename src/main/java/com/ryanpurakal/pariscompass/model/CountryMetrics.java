package com.ryanpurakal.pariscompass.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Latest available value of each headline metric for one country.
 * Sources end in different years (e.g. CO2 2024, temperature 2025), so {@code years} gives the year
 * of each value, keyed by field name, instead of one misleading snapshot year.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountryMetrics {
    private String iso3;
    private String name;
    private @Schema(nullable = true) Double co2PerCapita;
    private @Schema(nullable = true) Double co2TotalMt;
    private @Schema(nullable = true) Double temperatureAnomalyC;
    private @Schema(nullable = true) Double renewablesSharePct;
    private Map<String, Integer> years;
    private SourceInfo source;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceInfo {
        private String co2;
        private String temp;
        private String renewables;
    }
}
