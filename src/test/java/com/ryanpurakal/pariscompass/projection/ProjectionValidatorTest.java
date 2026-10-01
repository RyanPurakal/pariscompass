package com.ryanpurakal.pariscompass.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionValidatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ProjectionValidator VALIDATOR = new ProjectionValidator(MAPPER, new ProjectionSchema(MAPPER));
    private static final int BASE = 2024;
    private static final double LAST = 1000.0;

    static String reply(String direction, String points) {
        return """
                {"summary": "Emissions have fallen steadily for a decade as coal generation was replaced by renewables.",
                 "co2Direction": "%s",
                 "projectedCo2Mt": [%s],
                 "keyDrivers": ["Coal phase-out"],
                 "risks": ["Slower grid buildout"],
                 "confidence": "medium"}
                """.formatted(direction, points);
    }

    static String points(double... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"year\":").append(BASE + 1 + i).append(",\"co2Mt\":").append(values[i]).append('}');
        }
        return sb.toString();
    }

    @Test
    void acceptsWellFormedConsistentReply() {
        var r = VALIDATOR.validate(reply("decreasing", points(980, 960, 940, 920, 900)), BASE, LAST);
        assertThat(r.errors()).isEmpty();
        assertThat(r.valid()).isTrue();
        assertThat(r.output().projectedCo2Mt()).hasSize(5);
        assertThat(r.output().projectedCo2Mt().get(4).co2Mt()).isEqualTo(900.0);
    }

    @Test
    void stripsMarkdownCodeFence() {
        var r = VALIDATOR.validate("```json\n" + reply("stable", points(1000, 1001, 1002, 1003, 1010)) + "\n```", BASE, LAST);
        assertThat(r.valid()).isTrue();
    }

    @Test
    void rejectsNonJson() {
        var r = VALIDATOR.validate("CO2 will probably go down.", BASE, LAST);
        assertThat(r.valid()).isFalse();
        assertThat(r.errors().get(0)).startsWith("not valid JSON");
    }

    @Test
    void rejectsSchemaViolations() {
        String fourPoints = reply("decreasing", points(980, 960, 940, 920));
        assertThat(VALIDATOR.validate(fourPoints, BASE, LAST).errors()).anyMatch(e -> e.contains("projectedCo2Mt"));

        String extraField = reply("decreasing", points(980, 960, 940, 920, 900)).replace("\"confidence\"", "\"score\": 50, \"confidence\"");
        assertThat(VALIDATOR.validate(extraField, BASE, LAST).valid()).isFalse();

        String badEnum = reply("down", points(980, 960, 940, 920, 900));
        assertThat(VALIDATOR.validate(badEnum, BASE, LAST).errors()).anyMatch(e -> e.contains("co2Direction"));
    }

    @Test
    void rejectsWrongYears() {
        String shifted = reply("decreasing", points(980, 960, 940, 920, 900)).replace("\"year\":2025", "\"year\":2024");
        assertThat(VALIDATOR.validate(shifted, BASE, LAST).errors())
                .containsExactly("projectedCo2Mt[0].year is 2024, expected 2025");
    }

    @Test
    void rejectsUnitSlipOutsidePlausibilityBand() {
        // Values in tonnes instead of million tonnes.
        var r = VALIDATOR.validate(reply("increasing", points(980e6, 960e6, 940e6, 920e6, 900e6)), BASE, LAST);
        assertThat(r.valid()).isFalse();
        assertThat(r.errors()).hasSize(5).allMatch(e -> e.contains("outside 50%-150%"));
    }

    @Test
    void rejectsDirectionThatContradictsTheNumbers() {
        var r = VALIDATOR.validate(reply("decreasing", points(1010, 1020, 1030, 1040, 1050)), BASE, LAST);
        assertThat(r.errors()).containsExactly("co2Direction is 'decreasing' but the numbers say 'increasing'");
    }

    @Test
    void directionRuleUsesTwoPercentBand() {
        assertThat(ProjectionValidator.direction(1000, 1020)).isEqualTo("stable");
        assertThat(ProjectionValidator.direction(1000, 1021)).isEqualTo("increasing");
        assertThat(ProjectionValidator.direction(1000, 979)).isEqualTo("decreasing");
    }
}
