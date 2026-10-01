package com.ryanpurakal.pariscompass.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether raw model text is a usable projection. Three layers:
 * <ol>
 *   <li>it parses as JSON (code fences are stripped defensively)</li>
 *   <li>it matches projection-schema.json</li>
 *   <li>it makes sense for this country: the five years follow the last observed year, every value is
 *       within a plausibility band of the last observed value, and the stated direction agrees with
 *       the numbers under the same rule the prompt gives the model</li>
 * </ol>
 */
@Component
public class ProjectionValidator {
    /** Projections must stay within [0.5x, 1.5x] of the last observed value. Catches unit slips and fabrication. */
    static final double MIN_RATIO = 0.5;
    static final double MAX_RATIO = 1.5;
    /** Changes within +/-2% of the last observed value count as "stable". */
    static final double STABLE_BAND = 0.02;
    static final int HORIZON = 5;

    private final ObjectMapper mapper;
    private final ProjectionSchema schema;

    public ProjectionValidator(ObjectMapper mapper, ProjectionSchema schema) {
        this.mapper = mapper;
        this.schema = schema;
    }

    public Result validate(String raw, int baseYear, double lastCo2Mt) {
        if (raw == null || raw.isBlank()) {
            return Result.invalid(List.of("empty response"));
        }
        JsonNode node;
        try {
            node = mapper.readTree(stripCodeFence(raw));
        } catch (JsonProcessingException e) {
            return Result.invalid(List.of("not valid JSON: " + e.getOriginalMessage()));
        }
        List<String> errors = new ArrayList<>(schema.validate(node));
        if (!errors.isEmpty()) {
            return Result.invalid(errors);
        }
        ProjectionOutput output = mapper.convertValue(node, ProjectionOutput.class);
        errors.addAll(checkSemantics(output, baseYear, lastCo2Mt));
        return errors.isEmpty() ? Result.valid(output) : Result.invalid(errors);
    }

    static List<String> checkSemantics(ProjectionOutput out, int baseYear, double lastCo2Mt) {
        List<String> errors = new ArrayList<>();
        List<ProjectionOutput.YearValue> points = out.projectedCo2Mt();
        for (int i = 0; i < points.size(); i++) {
            ProjectionOutput.YearValue p = points.get(i);
            int expectedYear = baseYear + 1 + i;
            if (p.year() != expectedYear) {
                errors.add("projectedCo2Mt[" + i + "].year is " + p.year() + ", expected " + expectedYear);
            }
            double ratio = lastCo2Mt == 0 ? Double.NaN : p.co2Mt() / lastCo2Mt;
            if (!(ratio >= MIN_RATIO && ratio <= MAX_RATIO)) {
                errors.add(String.format(Locale.ROOT,
                        "projectedCo2Mt[%d].co2Mt is %.3f, outside %.0f%%-%.0f%% of the last observed %.3f",
                        i, p.co2Mt(), MIN_RATIO * 100, MAX_RATIO * 100, lastCo2Mt));
            }
        }
        if (errors.isEmpty()) {
            String expected = direction(lastCo2Mt, points.get(points.size() - 1).co2Mt());
            if (!expected.equals(out.co2Direction())) {
                errors.add("co2Direction is '" + out.co2Direction() + "' but the numbers say '" + expected + "'");
            }
        }
        return errors;
    }

    /** The rule stated in the prompt: more than 2% up is increasing, more than 2% down is decreasing. */
    static String direction(double from, double to) {
        double change = (to - from) / from;
        return change > STABLE_BAND ? "increasing" : change < -STABLE_BAND ? "decreasing" : "stable";
    }

    private static String stripCodeFence(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "");
            s = s.replaceFirst("\\s*```$", "");
        }
        return s;
    }

    public record Result(boolean valid, ProjectionOutput output, List<String> errors) {
        static Result valid(ProjectionOutput output) {
            return new Result(true, output, List.of());
        }

        static Result invalid(List<String> errors) {
            return new Result(false, null, List.copyOf(errors));
        }
    }
}
