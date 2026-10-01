package com.ryanpurakal.pariscompass.domain;

import java.io.Serializable;

/** Composite key matching observation's primary key (iso3, metric_code, year). */
public record ObservationId(String iso3, String metricCode, Short year) implements Serializable {
    public ObservationId() {
        this(null, null, null);
    }
}
