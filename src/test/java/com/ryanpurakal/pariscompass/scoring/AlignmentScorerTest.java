package com.ryanpurakal.pariscompass.scoring;

import com.ryanpurakal.pariscompass.model.SeriesPoint;
import com.ryanpurakal.pariscompass.scoring.AlignmentScorer.AlignmentResult;
import com.ryanpurakal.pariscompass.scoring.AlignmentScorer.Band;
import com.ryanpurakal.pariscompass.scoring.AlignmentScorer.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Expected values are worked out by hand in the comments so the formula can be checked on paper. */
class AlignmentScorerTest {

    /** value(year) = start * (1 + rate)^(year - first): an exact compound-growth series. */
    private static List<SeriesPoint> compound(int first, int last, double start, double rate) {
        return IntStream.rangeClosed(first, last)
                .mapToObj(y -> new SeriesPoint(y, start * Math.pow(1 + rate, y - first))).toList();
    }

    /** value(year) = start + step * (year - first). */
    private static List<SeriesPoint> linearSeries(int first, int last, double start, double step) {
        return IntStream.rangeClosed(first, last).mapToObj(y -> new SeriesPoint(y, start + step * (y - first))).toList();
    }

    private static Component component(AlignmentResult r, String key) {
        return r.components().stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void linearMapHitsAnchorsAndClamps() {
        assertThat(AlignmentScorer.linear(2.0, 2.0, -6.0)).isEqualTo(0);
        assertThat(AlignmentScorer.linear(-6.0, 2.0, -6.0)).isEqualTo(100);
        assertThat(AlignmentScorer.linear(-2.0, 2.0, -6.0)).isEqualTo(50);
        assertThat(AlignmentScorer.linear(-10.0, 2.0, -6.0)).isEqualTo(100);
        assertThat(AlignmentScorer.linear(5.0, 2.0, -6.0)).isEqualTo(0);
    }

    @Test
    void logLinearFitRecoversExactCompoundRate() {
        // 5% annual decline: (-5 - 2) / (-6 - 2) * 100 = 87.5
        Component c = AlignmentScorer.emissionsTrend(compound(2015, 2024, 1000, -0.05));
        assertThat(c.value()).isCloseTo(-5.0, within(1e-6));
        assertThat(c.subScore()).isEqualTo(87.5);
        assertThat(c.fromYear()).isEqualTo(2015);
        assertThat(c.toYear()).isEqualTo(2024);
        assertThat(c.points()).isEqualTo(10);
    }

    @Test
    void fullScoreCombinesWeightedComponents() {
        // trend 87.5; level 8.5 t -> (8.5-15)/(2-15)*100 = 50; clean 48% in 2024 -> 48;
        // momentum +1 pp/yr vs required (100-48)/(2050-2024) = 2 pp/yr -> 50
        // 0.40*87.5 + 0.25*50 + 0.20*48 + 0.15*50 = 35 + 12.5 + 9.6 + 7.5 = 64.6
        AlignmentResult r = AlignmentScorer.score(
                compound(2015, 2024, 1000, -0.05),
                List.of(new SeriesPoint(2024, 8.5)),
                linearSeries(2015, 2024, 39, 1.0));

        assertThat(component(r, "emissions_level").subScore()).isEqualTo(50.0);
        assertThat(component(r, "clean_power_level").subScore()).isEqualTo(48.0);
        assertThat(component(r, "clean_power_momentum").value()).isCloseTo(1.0, within(1e-9));
        assertThat(component(r, "clean_power_momentum").subScore()).isEqualTo(50.0);
        assertThat(component(r, "clean_power_momentum").note()).isEqualTo("needs 2.00 pp/yr to reach 100% by 2050");
        assertThat(r.score()).isEqualTo(64.6);
        assertThat(r.band()).isEqualTo(Band.MEDIUM);
        assertThat(r.formulaVersion()).isEqualTo("v1");
        assertThat(r.components()).allSatisfy(c -> assertThat(c.effectiveWeight()).isEqualTo(c.weight()));
    }

    @Test
    void nearlyCleanGridIsNotPenalizedForSlowGrowth() {
        // 99% low-carbon in 2024 needs only 1/26 = 0.038 pp/yr; +0.1 pp/yr already exceeds it.
        Component c = AlignmentScorer.cleanPowerMomentum(linearSeries(2015, 2024, 98.1, 0.1));
        assertThat(c.subScore()).isEqualTo(100.0);
        assertThat(c.note()).isEqualTo("needs 0.04 pp/yr to reach 100% by 2050");
    }

    @Test
    void fullyCleanGridScoresFullMomentum() {
        Component c = AlignmentScorer.cleanPowerMomentum(linearSeries(2015, 2024, 100, 0));
        assertThat(c.subScore()).isEqualTo(100.0);
    }

    @Test
    void missingComponentsAreDroppedAndWeightsRescaled() {
        // No electricity data: (0.40*87.5 + 0.25*50) / 0.65 = 47.5 / 0.65 = 73.08 -> 73.1
        AlignmentResult r = AlignmentScorer.score(
                compound(2015, 2024, 1000, -0.05), List.of(new SeriesPoint(2024, 8.5)), List.of());

        assertThat(r.score()).isEqualTo(73.1);
        assertThat(r.band()).isEqualTo(Band.HIGH);
        assertThat(component(r, "emissions_trend").effectiveWeight()).isEqualTo(0.6154);
        assertThat(component(r, "clean_power_level").available()).isFalse();
        assertThat(component(r, "clean_power_level").effectiveWeight()).isNull();
    }

    @Test
    void noScoreWithoutEnoughEmissionsHistory() {
        AlignmentResult r = AlignmentScorer.score(
                compound(2020, 2024, 1000, -0.05), List.of(new SeriesPoint(2024, 8.5)), linearSeries(2015, 2024, 31, 1));

        assertThat(r.score()).isNull();
        assertThat(r.band()).isNull();
        assertThat(r.reason()).contains("need 6 of the last 10 years, have 5");
    }

    @Test
    void onlyTheLastTenYearsCount() {
        // A huge 1990 value must not affect the trend over 2015-2024.
        List<SeriesPoint> series = new ArrayList<>(compound(2015, 2024, 1000, 0.02));
        series.add(new SeriesPoint(1990, 1_000_000));
        Component c = AlignmentScorer.emissionsTrend(series);
        assertThat(c.value()).isCloseTo(2.0, within(1e-6));
        assertThat(c.subScore()).isEqualTo(0.0);
    }

    @Test
    void gapsInsideWindowAreToleratedDownToSixPoints() {
        List<SeriesPoint> sparse = compound(2015, 2024, 1000, -0.06).stream()
                .filter(p -> p.year() % 5 != 0 && p.year() != 2017 && p.year() != 2023).toList();
        assertThat(sparse).hasSize(6);
        Component c = AlignmentScorer.emissionsTrend(sparse);
        assertThat(c.available()).isTrue();
        assertThat(c.subScore()).isEqualTo(100.0);
    }

    @Test
    void zeroEmissionsMakeTrendUnavailable() {
        List<SeriesPoint> series = new ArrayList<>(compound(2015, 2023, 10, -0.05));
        series.add(new SeriesPoint(2024, 0.0));
        Component c = AlignmentScorer.emissionsTrend(series);
        assertThat(c.available()).isFalse();
        assertThat(c.note()).contains("log trend undefined");
    }

    @Test
    void decliningCleanShareScoresZeroMomentum() {
        Component c = AlignmentScorer.cleanPowerMomentum(linearSeries(2015, 2024, 60, -0.5));
        assertThat(c.value()).isCloseTo(-0.5, within(1e-9));
        assertThat(c.subScore()).isEqualTo(0.0);
    }

    @Test
    void bandThresholds() {
        assertThat(Band.of(70.0)).isEqualTo(Band.HIGH);
        assertThat(Band.of(69.9)).isEqualTo(Band.MEDIUM);
        assertThat(Band.of(40.0)).isEqualTo(Band.MEDIUM);
        assertThat(Band.of(39.9)).isEqualTo(Band.LOW);
    }

    @Test
    void sameInputGivesSameOutput() {
        List<SeriesPoint> co2 = compound(2015, 2024, 500, -0.013);
        List<SeriesPoint> pc = List.of(new SeriesPoint(2024, 6.1));
        List<SeriesPoint> clean = linearSeries(2014, 2024, 20, 0.7);
        assertThat(AlignmentScorer.score(co2, pc, clean)).isEqualTo(AlignmentScorer.score(co2, pc, clean));
    }
}
