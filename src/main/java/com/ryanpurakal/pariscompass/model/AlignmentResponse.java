package com.ryanpurakal.pariscompass.model;

import com.ryanpurakal.pariscompass.scoring.AlignmentScorer;

import java.util.List;

/** Alignment score with every input shown, so a client can explain the number instead of just displaying it. */
public record AlignmentResponse(
        String iso3,
        String name,
        String formulaVersion,
        Double score,
        AlignmentScorer.Band band,
        List<AlignmentScorer.Component> components,
        String reason,
        String disclaimer) {
}
