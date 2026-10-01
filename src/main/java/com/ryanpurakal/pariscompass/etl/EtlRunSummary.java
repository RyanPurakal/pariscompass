package com.ryanpurakal.pariscompass.etl;

import java.util.List;

public record EtlRunSummary(long runId, boolean succeeded, long durationMs, List<SourceResult> sources) {
}
