package com.ryanpurakal.pariscompass.etl;

import java.util.Map;

/** Outcome of ingesting one source in one run. Persisted to etl_source_result. */
public record SourceResult(
        DataSource source,
        String location,
        String sha256,
        long bytes,
        boolean skippedUnchanged,
        int rowsRead,
        int rowsSkippedAggregate,
        int rowsSkippedNonIso,
        int rowsRejected,
        int valuesAccepted,
        int valuesRejected,
        int valuesInserted,
        int valuesUpdated,
        int valuesUnchanged,
        int nameMismatches,
        Map<RejectionReason, Long> rejectionsByReason,
        long durationMs,
        String error) {

    public boolean failed() {
        return error != null;
    }

    static SourceResult failure(DataSource source, String location, long durationMs, String error) {
        return new SourceResult(source, location, null, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                Map.of(), durationMs, error);
    }

    static SourceResult skipped(DataSource source, String location, String sha256, long bytes, long durationMs) {
        return new SourceResult(source, location, sha256, bytes, true, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                Map.of(), durationMs, null);
    }
}
