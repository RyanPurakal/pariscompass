package com.ryanpurakal.pariscompass.model;

import java.util.List;

public record CountrySeriesResponse(String iso3, String name, Integer from, Integer to, List<MetricSeries> series) {
}
