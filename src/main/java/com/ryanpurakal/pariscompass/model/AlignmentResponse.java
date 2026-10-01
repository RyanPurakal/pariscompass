package com.ryanpurakal.pariscompass.model;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ryanpurakal.pariscompass.scoring.AlignmentScorer;

import java.util.List;

/** Alignment score with every input shown, so a client can explain the number instead of just displaying it. */
public record AlignmentResponse(
        String iso3,
        String name,
        String formulaVersion,
        @Schema(nullable = true) Double score,
        @Schema(nullable = true) AlignmentScorer.Band band,
        List<AlignmentScorer.Component> components,
        @Schema(nullable = true) String reason,
        String disclaimer) {
}
