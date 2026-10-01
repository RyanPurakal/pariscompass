package com.ryanpurakal.pariscompass.model;

import com.ryanpurakal.pariscompass.projection.ProjectionOutput;
import com.ryanpurakal.pariscompass.scoring.AlignmentScorer;

import java.time.Instant;
import java.util.List;

/**
 * A five-year projection plus how it was produced.
 * <ul>
 *   <li>{@code generatedBy}: "model" or "trend-extrapolation" (the deterministic fallback)</li>
 *   <li>{@code status}: VALID (first model reply passed validation), REPAIRED (passed after one retry), FALLBACK</li>
 *   <li>{@code alignmentScore}/{@code alignmentBand}: from the deterministic scorer, never from the model</li>
 * </ul>
 */
public record ProjectionResponse(
        Long id,
        String iso3,
        String country,
        String generatedBy,
        String model,
        String promptVersion,
        String status,
        int attempts,
        String fallbackReason,
        List<String> validationErrors,
        Instant generatedAt,
        boolean cached,
        long latencyMs,
        int baseYear,
        double baseYearCo2Mt,
        Double alignmentScore,
        AlignmentScorer.Band alignmentBand,
        ProjectionOutput projection) {

    public ProjectionResponse asCached() {
        return new ProjectionResponse(id, iso3, country, generatedBy, model, promptVersion, status, attempts,
                fallbackReason, validationErrors, generatedAt, true, latencyMs, baseYear, baseYearCo2Mt,
                alignmentScore, alignmentBand, projection);
    }
}
