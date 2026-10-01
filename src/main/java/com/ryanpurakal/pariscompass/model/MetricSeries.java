package com.ryanpurakal.pariscompass.model;

import java.util.List;

/** One metric's values over time, ordered by year. Years without data are absent, not null. */
public record MetricSeries(String metric, String unit, List<SeriesPoint> points) {
}
