package com.ryanpurakal.pariscompass.etl;

/**
 * Why a row or value was not ingested. Row-level reasons drop every value in the row;
 * value-level reasons drop a single metric value.
 */
public enum RejectionReason {
    /** Row: code is not an ISO 3166-1 alpha-3 code (e.g. dissolved countries like ANT). */
    UNKNOWN_ISO3,
    /** Row: year is not an integer. */
    INVALID_YEAR,
    /** Row: year is before 1750 or after the current year. */
    YEAR_OUT_OF_RANGE,
    /** Row: the same (country, year) already appeared earlier in this file. */
    DUPLICATE_ROW,
    /** Value: not a finite number. */
    NON_NUMERIC,
    /** Value: outside the metric's plausibility bounds. */
    OUT_OF_RANGE
}
