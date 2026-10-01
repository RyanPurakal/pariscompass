package com.ryanpurakal.pariscompass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountryMetrics {
    private String iso3;
    private String name;
    private Integer year;
    private Double co2PerCapita;
    private Double co2TotalMt;
    private Double temperatureAnomalyC;
    private Double renewablesSharePct;
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

