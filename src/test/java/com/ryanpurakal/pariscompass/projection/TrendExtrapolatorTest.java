package com.ryanpurakal.pariscompass.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryanpurakal.pariscompass.model.SeriesPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrendExtrapolatorTest {

    private static List<SeriesPoint> compound(double start, double rate) {
        return IntStream.rangeClosed(2015, 2024)
                .mapToObj(y -> new SeriesPoint(y, start * Math.pow(1 + rate, y - 2015))).toList();
    }

    @Test
    void continuesTheFittedCompoundRate() {
        List<SeriesPoint> window = compound(1000, -0.04);
        double last = window.get(window.size() - 1).value();
        ProjectionOutput out = TrendExtrapolator.extrapolate(window, 2024, last);

        assertThat(out.projectedCo2Mt()).extracting(ProjectionOutput.YearValue::year)
                .containsExactly(2025, 2026, 2027, 2028, 2029);
        assertThat(out.projectedCo2Mt().get(0).co2Mt()).isCloseTo(last * 0.96, within(0.001));
        assertThat(out.projectedCo2Mt().get(4).co2Mt()).isCloseTo(last * Math.pow(0.96, 5), within(0.001));
        assertThat(out.co2Direction()).isEqualTo("decreasing");
        assertThat(out.confidence()).isEqualTo("low");
        assertThat(out.summary()).startsWith("Statistical fallback, not an AI projection.").contains("-4.00% per year");
    }

    @Test
    void fallbackOutputPassesTheSameValidationAsModelOutput() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ProjectionValidator validator = new ProjectionValidator(mapper, new ProjectionSchema(mapper));
        for (double rate : new double[]{-0.05, 0.0, 0.03}) {
            List<SeriesPoint> window = compound(500, rate);
            double last = window.get(window.size() - 1).value();
            String json = mapper.writeValueAsString(TrendExtrapolator.extrapolate(window, 2024, last));
            assertThat(validator.validate(json, 2024, last).errors()).as("rate %s", rate).isEmpty();
        }
    }
}
