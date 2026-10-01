package com.ryanpurakal.pariscompass.etl;

import java.util.List;
import java.util.Map;

/**
 * Everything learned from one source file before touching the database.
 * {@code countryNames} holds the name each accepted ISO3 code had in this file.
 */
public record ParseResult(
        DataSource source,
        int rowsRead,
        int rowsSkippedAggregate,
        int rowsSkippedNonIso,
        List<ParsedObservation> accepted,
        List<Rejection> rejections,
        Map<String, String> countryNames) {

    public int rowsRejected() {
        return (int) rejections.stream().filter(Rejection::isRowLevel).count();
    }

    public int valuesRejected() {
        return rejections.size() - rowsRejected();
    }
}
