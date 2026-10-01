package com.ryanpurakal.pariscompass.scoring;

import com.ryanpurakal.pariscompass.model.SeriesPoint;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic Paris Agreement alignment score, formula v1. Pure function of the historical series;
 * no I/O, no randomness, no LLM. See SCORING.md for the rationale behind every constant.
 *
 * <pre>
 * component              weight  input                                         100 at        0 at
 * emissions_trend         0.40   CO2 compound annual change, last 10 years     <= -6 %/yr    >= +2 %/yr
 * emissions_level         0.25   latest CO2 per capita                         <= 2 t        >= 15 t
 * clean_power_level       0.20   latest low-carbon share of electricity        100 %         0 %
 * clean_power_momentum    0.15   change in that share per year, last 10 years  >= required    <= 0 pp/yr
 *                                (required = (100 - latest share) / (2050 - latest year))
 * </pre>
 * Sub-scores are linear between the two anchors and clamped to [0, 100]. Missing components are
 * dropped and the remaining weights rescaled to sum to 1. Without emissions_trend there is no score.
 */
public final class AlignmentScorer {
    public static final String FORMULA_VERSION = "v1";
    static final int WINDOW_YEARS = 10;
    static final int MIN_POINTS = 6;
    static final int TARGET_YEAR = 2050;

    static final double W_TREND = 0.40;
    static final double W_LEVEL = 0.25;
    static final double W_CLEAN = 0.20;
    static final double W_MOMENTUM = 0.15;

    private AlignmentScorer() {
    }

    /**
     * @param co2TotalMt         annual CO2 emissions, any order
     * @param co2PerCapitaT      annual CO2 per capita
     * @param lowCarbonShareElec annual low-carbon share of electricity, %
     */
    public static AlignmentResult score(List<SeriesPoint> co2TotalMt, List<SeriesPoint> co2PerCapitaT,
                                        List<SeriesPoint> lowCarbonShareElec) {
        List<Component> components = List.of(
                emissionsTrend(co2TotalMt),
                emissionsLevel(co2PerCapitaT),
                cleanPowerLevel(lowCarbonShareElec),
                cleanPowerMomentum(lowCarbonShareElec));

        Component trend = components.get(0);
        if (!trend.available()) {
            return new AlignmentResult(FORMULA_VERSION, null, null, components,
                    "No score: emissions trend unavailable (" + trend.note() + ")");
        }
        double totalWeight = components.stream().filter(Component::available).mapToDouble(Component::weight).sum();
        double score = 0;
        List<Component> weighted = new ArrayList<>();
        for (Component c : components) {
            if (c.available()) {
                double effective = c.weight() / totalWeight;
                score += effective * c.subScore();
                weighted.add(c.withEffectiveWeight(effective));
            } else {
                weighted.add(c);
            }
        }
        double rounded = Math.round(score * 10) / 10.0;
        return new AlignmentResult(FORMULA_VERSION, rounded, Band.of(rounded), weighted, null);
    }

    static Component emissionsTrend(List<SeriesPoint> series) {
        Window w = lastWindow(series);
        if (w.points.size() < MIN_POINTS) {
            return Component.missing("emissions_trend", W_TREND, "co2_total_mt", "%/yr", notEnough(w));
        }
        if (w.points.stream().anyMatch(p -> p.value() <= 0)) {
            return Component.missing("emissions_trend", W_TREND, "co2_total_mt", "%/yr",
                    "zero or negative emissions in window; log trend undefined");
        }
        // Least squares on ln(emissions) vs year gives the compound annual growth rate exp(slope) - 1.
        double slope = slope(w.points, true);
        double ratePct = (Math.exp(slope) - 1) * 100;
        return Component.of("emissions_trend", W_TREND, "co2_total_mt", ratePct, "%/yr", w,
                linear(ratePct, 2.0, -6.0));
    }

    static Component emissionsLevel(List<SeriesPoint> series) {
        Optional<SeriesPoint> latest = latest(series);
        if (latest.isEmpty()) {
            return Component.missing("emissions_level", W_LEVEL, "co2_per_capita_t", "t CO2/person", "no data");
        }
        double v = latest.get().value();
        Window w = new Window(latest.get().year(), latest.get().year(), List.of(latest.get()));
        return Component.of("emissions_level", W_LEVEL, "co2_per_capita_t", v, "t CO2/person", w, linear(v, 15.0, 2.0));
    }

    static Component cleanPowerLevel(List<SeriesPoint> series) {
        Optional<SeriesPoint> latest = latest(series);
        if (latest.isEmpty()) {
            return Component.missing("clean_power_level", W_CLEAN, "low_carbon_share_elec_pct", "%", "no data");
        }
        double v = latest.get().value();
        Window w = new Window(latest.get().year(), latest.get().year(), List.of(latest.get()));
        return Component.of("clean_power_level", W_CLEAN, "low_carbon_share_elec_pct", v, "%", w, linear(v, 0.0, 100.0));
    }

    /**
     * Pace of the low-carbon share relative to the pace this country needs to reach 100% by 2050
     * from its latest share. Measuring against a fixed pace (e.g. +2 pp/yr for everyone) would
     * penalize grids that are already almost fully low-carbon and cannot grow much further.
     */
    static Component cleanPowerMomentum(List<SeriesPoint> series) {
        Window w = lastWindow(series);
        if (w.points.size() < MIN_POINTS) {
            return Component.missing("clean_power_momentum", W_MOMENTUM, "low_carbon_share_elec_pct", "pp/yr", notEnough(w));
        }
        double ppPerYear = slope(w.points, false);
        SeriesPoint latest = latest(series).orElseThrow();
        double gap = Math.max(0, 100 - latest.value());
        int yearsLeft = Math.max(1, TARGET_YEAR - latest.year());
        double requiredPace = gap / yearsLeft;
        double sub = requiredPace == 0 ? 100 : linear(ppPerYear, 0.0, requiredPace);
        String note = String.format(java.util.Locale.ROOT, "needs %.2f pp/yr to reach 100%% by %d", requiredPace, TARGET_YEAR);
        return Component.of("clean_power_momentum", W_MOMENTUM, "low_carbon_share_elec_pct", ppPerYear, "pp/yr", w, sub)
                .withNote(note);
    }

    /** Linear map from {@code zeroAt} (0 points) to {@code fullAt} (100 points), clamped. Works in either direction. */
    static double linear(double x, double zeroAt, double fullAt) {
        double s = 100 * (x - zeroAt) / (fullAt - zeroAt);
        return Math.max(0, Math.min(100, s));
    }

    /** Ordinary least squares slope of value (or ln(value)) against year. */
    static double slope(List<SeriesPoint> points, boolean logValues) {
        int n = points.size();
        double meanX = points.stream().mapToDouble(SeriesPoint::year).average().orElseThrow();
        double meanY = points.stream().mapToDouble(p -> y(p, logValues)).average().orElseThrow();
        double num = 0;
        double den = 0;
        for (SeriesPoint p : points) {
            double dx = p.year() - meanX;
            num += dx * (y(p, logValues) - meanY);
            den += dx * dx;
        }
        return n < 2 || den == 0 ? 0 : num / den;
    }

    private static double y(SeriesPoint p, boolean log) {
        return log ? Math.log(p.value()) : p.value();
    }

    /** Points within the WINDOW_YEARS years ending at the series' latest year. */
    static Window lastWindow(List<SeriesPoint> series) {
        Optional<SeriesPoint> latest = latest(series);
        if (latest.isEmpty()) {
            return new Window(null, null, List.of());
        }
        int to = latest.get().year();
        int from = to - WINDOW_YEARS + 1;
        List<SeriesPoint> points = series.stream().filter(p -> p.year() >= from && p.year() <= to).toList();
        return new Window(from, to, points);
    }

    private static Optional<SeriesPoint> latest(List<SeriesPoint> series) {
        return series == null ? Optional.empty()
                : series.stream().max((a, b) -> Integer.compare(a.year(), b.year()));
    }

    private static String notEnough(Window w) {
        return "need " + MIN_POINTS + " of the last " + WINDOW_YEARS + " years, have " + w.points.size();
    }

    record Window(Integer fromYear, Integer toYear, List<SeriesPoint> points) {
    }

    public enum Band {
        HIGH, MEDIUM, LOW;

        static Band of(double score) {
            return score >= 70 ? HIGH : score >= 40 ? MEDIUM : LOW;
        }
    }

    /**
     * One input to the score. {@code weight} is the nominal weight; {@code effectiveWeight} is after
     * rescaling for missing components (null when this component is missing).
     */
    public record Component(String key, double weight, @Schema(nullable = true) Double effectiveWeight,
                            boolean available, String metric, @Schema(nullable = true) Double value, String unit,
                            @Schema(nullable = true) Integer fromYear, @Schema(nullable = true) Integer toYear, int points,
                            @Schema(nullable = true) Double subScore, @Schema(nullable = true) String note) {

        static Component of(String key, double weight, String metric, double value, String unit, Window w, double subScore) {
            return new Component(key, weight, null, true, metric, round(value, 3), unit, w.fromYear(), w.toYear(),
                    w.points().size(), round(subScore, 1), null);
        }

        static Component missing(String key, double weight, String metric, String unit, String note) {
            return new Component(key, weight, null, false, metric, null, unit, null, null, 0, null, note);
        }

        Component withNote(String newNote) {
            return new Component(key, weight, effectiveWeight, available, metric, value, unit, fromYear, toYear,
                    points, subScore, newNote);
        }

        Component withEffectiveWeight(double effective) {
            return new Component(key, weight, round(effective, 4), available, metric, value, unit, fromYear, toYear,
                    points, subScore, note);
        }

        private static double round(double v, int places) {
            double f = Math.pow(10, places);
            return Math.round(v * f) / f;
        }
    }

    /** {@code score} and {@code band} are null when {@code reason} explains why no score exists. */
    public record AlignmentResult(String formulaVersion, Double score, Band band, List<Component> components,
                                  String reason) {
    }
}
