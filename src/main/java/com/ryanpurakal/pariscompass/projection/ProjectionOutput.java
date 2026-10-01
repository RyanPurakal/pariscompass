package com.ryanpurakal.pariscompass.projection;

import java.util.List;

/** The structured projection, whether it came from the model or the statistical fallback. Mirrors projection-schema.json. */
public record ProjectionOutput(
        String summary,
        String co2Direction,
        List<YearValue> projectedCo2Mt,
        List<String> keyDrivers,
        List<String> risks,
        String confidence) {

    public record YearValue(int year, double co2Mt) {
    }
}
