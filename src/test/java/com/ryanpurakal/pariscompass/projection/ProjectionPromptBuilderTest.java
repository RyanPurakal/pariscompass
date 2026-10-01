package com.ryanpurakal.pariscompass.projection;

import com.ryanpurakal.pariscompass.model.AlignmentResponse;
import com.ryanpurakal.pariscompass.model.SeriesPoint;
import com.ryanpurakal.pariscompass.scoring.AlignmentScorer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionPromptBuilderTest {
    private static final AlignmentResponse ALIGNMENT = new AlignmentResponse("USA", "United States", "v1", 30.6,
            AlignmentScorer.Band.LOW, List.of(), null, "d");
    private static final Map<String, List<SeriesPoint>> HISTORY = Map.of(
            "co2_total_mt", List.of(new SeriesPoint(1990, 5000), new SeriesPoint(2023, 4900.5), new SeriesPoint(2024, 4904.12)),
            "low_carbon_share_elec_pct", List.of(new SeriesPoint(2024, 43.002)));

    @Test
    void groundsPromptInTheCountrysOwnData() {
        String p = ProjectionPromptBuilder.build("USA", "United States", HISTORY, 2024, 4904.12, ALIGNMENT);

        assertThat(p).contains("United States (USA)", "from 2025 to 2029")
                .contains("2023 | 4900.5 |", "2024 | 4904.12 |")
                .contains("| 43.002 |")
                .contains("30.6/100, band LOW", "do not recompute or change it")
                .contains("compares 2029 with 2024 (4904.12 Mt)");
    }

    @Test
    void onlyIncludesTheLastThirtyYears() {
        String p = ProjectionPromptBuilder.build("USA", "United States", HISTORY, 2024, 4904.12, ALIGNMENT);
        assertThat(p).doesNotContain("1990 |");
    }

    @Test
    void isDeterministicSoItCanBeHashedAsACacheKey() {
        assertThat(ProjectionPromptBuilder.build("USA", "United States", HISTORY, 2024, 4904.12, ALIGNMENT))
                .isEqualTo(ProjectionPromptBuilder.build("USA", "United States", HISTORY, 2024, 4904.12, ALIGNMENT));
    }

    @Test
    void repairSuffixListsEveryError() {
        assertThat(ProjectionPromptBuilder.repairSuffix(List.of("a is wrong", "b is wrong")))
                .contains("- a is wrong\n- b is wrong");
    }
}
