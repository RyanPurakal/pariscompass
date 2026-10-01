package com.ryanpurakal.pariscompass.etl;

/** One rejected row ({@code metricCode == null}) or value, with the raw input kept for auditing. */
public record Rejection(int lineNumber, String iso3, String year, String metricCode, String rawValue,
                        RejectionReason reason, String detail) {

    public boolean isRowLevel() {
        return metricCode == null;
    }
}
