package com.ryanpurakal.pariscompass.etl;

/** A validated value ready to be written. */
public record ParsedObservation(String iso3, String metricCode, int year, double value) {
}
