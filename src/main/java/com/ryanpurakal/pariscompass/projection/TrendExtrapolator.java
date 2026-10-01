package com.ryanpurakal.pariscompass.projection;

import com.ryanpurakal.pariscompass.model.SeriesPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic fallback when the model is unavailable or its output fails validation twice:
 * continue the 10-year log-linear CO2 trend for five years. Clearly labeled, never presented as AI output.
 */
public final class TrendExtrapolator {
    static final int WINDOW_YEARS = 10;

    private TrendExtrapolator() {
    }

    /** @param window CO2 points from the last WINDOW_YEARS years, all positive, at least two */
    public static ProjectionOutput extrapolate(List<SeriesPoint> window, int baseYear, double lastCo2Mt) {
        double slope = logSlope(window);
        double ratePct = (Math.exp(slope) - 1) * 100;
        List<ProjectionOutput.YearValue> points = new ArrayList<>();
        for (int k = 1; k <= ProjectionValidator.HORIZON; k++) {
            double value = lastCo2Mt * Math.exp(slope * k);
            points.add(new ProjectionOutput.YearValue(baseYear + k, Math.round(value * 1000) / 1000.0));
        }
        int from = window.stream().mapToInt(SeriesPoint::year).min().orElse(baseYear);
        String summary = String.format(Locale.ROOT,
                "Statistical fallback, not an AI projection. CO2 emissions changed by %.2f%% per year on average "
                        + "from %d to %d (log-linear fit over %d data points). This projection assumes that trend "
                        + "continues unchanged through %d, so it does not account for new policies, economic shocks "
                        + "or changes in the energy mix.",
                ratePct, from, baseYear, window.size(), baseYear + ProjectionValidator.HORIZON);
        return new ProjectionOutput(
                summary,
                ProjectionValidator.direction(lastCo2Mt, points.get(points.size() - 1).co2Mt()),
                points,
                List.of(String.format(Locale.ROOT, "Continuation of the %d-%d emissions trend (%.2f%%/yr)", from, baseYear, ratePct)),
                List.of("Assumes no change in policy, economy or energy mix"),
                "low");
    }

    static double logSlope(List<SeriesPoint> points) {
        double meanX = points.stream().mapToDouble(SeriesPoint::year).average().orElseThrow();
        double meanY = points.stream().mapToDouble(p -> Math.log(p.value())).average().orElseThrow();
        double num = 0;
        double den = 0;
        for (SeriesPoint p : points) {
            double dx = p.year() - meanX;
            num += dx * (Math.log(p.value()) - meanY);
            den += dx * dx;
        }
        return den == 0 ? 0 : num / den;
    }
}
