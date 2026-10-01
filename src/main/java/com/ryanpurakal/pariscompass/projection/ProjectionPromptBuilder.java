package com.ryanpurakal.pariscompass.projection;

import com.ryanpurakal.pariscompass.etl.MetricDefinition;
import com.ryanpurakal.pariscompass.model.AlignmentResponse;
import com.ryanpurakal.pariscompass.model.SeriesPoint;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Builds the prompt from the country's real history. Bump PROMPT_VERSION whenever the wording or the
 * data included changes: it is stored with every projection so old and new outputs can be compared.
 * The prompt is deterministic for the same data, so its hash works as the cache key.
 */
public final class ProjectionPromptBuilder {
    public static final String PROMPT_VERSION = "p1";
    static final int HISTORY_YEARS = 30;

    /** Columns of the history table, in order. */
    static final List<MetricDefinition> COLUMNS = List.of(
            MetricDefinition.CO2_TOTAL_MT,
            MetricDefinition.CO2_PER_CAPITA_T,
            MetricDefinition.LOW_CARBON_SHARE_ELEC_PCT,
            MetricDefinition.RENEWABLES_SHARE_ELEC_PCT,
            MetricDefinition.TEMPERATURE_ANOMALY_C);

    public static final List<String> COLUMNS_CODES = COLUMNS.stream().map(MetricDefinition::code).toList();

    private ProjectionPromptBuilder() {
    }

    /**
     * @param history   series keyed by metric code (any years; only the last HISTORY_YEARS up to baseYear are used)
     * @param baseYear  last year with observed CO2; the projection covers baseYear+1 to baseYear+5
     */
    public static String build(String iso3, String name, Map<String, List<SeriesPoint>> history, int baseYear,
                               double lastCo2Mt, AlignmentResponse alignment) {
        int fromYear = baseYear - HISTORY_YEARS + 1;
        int last = baseYear + ProjectionValidator.HORIZON;
        StringBuilder p = new StringBuilder();
        p.append("You are a climate data analyst. Project the territorial CO2 emissions of ")
                .append(name).append(" (").append(iso3).append(") for each year from ")
                .append(baseYear + 1).append(" to ").append(last).append(".\n\n");

        p.append("Historical data, ").append(fromYear).append(" to ").append(baseYear)
                .append(". Sources: Our World in Data (Global Carbon Project, Ember, Energy Institute) and ")
                .append("Copernicus ERA5 for temperature. Blank means no data. Use only this data; do not cite ")
                .append("other sources or invent historical numbers.\n\n");
        p.append("year");
        for (MetricDefinition m : COLUMNS) {
            p.append(" | ").append(m.displayName()).append(" (").append(m.unit()).append(")");
        }
        p.append('\n');
        Map<String, Map<Integer, Double>> byYear = COLUMNS.stream().collect(Collectors.toMap(MetricDefinition::code,
                m -> history.getOrDefault(m.code(), List.of()).stream()
                        .collect(Collectors.toMap(SeriesPoint::year, SeriesPoint::value, (a, b) -> a))));
        TreeSet<Integer> years = new TreeSet<>();
        byYear.values().forEach(v -> v.keySet().stream().filter(y -> y >= fromYear && y <= baseYear).forEach(years::add));
        for (int year : years) {
            p.append(year);
            for (MetricDefinition m : COLUMNS) {
                Double v = byYear.get(m.code()).get(year);
                p.append(" | ").append(v == null ? "" : format(v));
            }
            p.append('\n');
        }

        p.append("\nParis alignment indicator, computed separately by a fixed formula (")
                .append(alignment.formulaVersion()).append("). Treat it as context; do not recompute or change it: ");
        if (alignment.score() == null) {
            p.append("not available (").append(alignment.reason()).append(").\n");
        } else {
            p.append(format(alignment.score())).append("/100, band ").append(alignment.band()).append(".\n");
        }

        p.append("\nInstructions:\n")
                .append("1. projectedCo2Mt: exactly 5 entries for the years ").append(baseYear + 1).append(" to ").append(last)
                .append(" in order, in million tonnes of CO2, the same unit as the table.\n")
                .append("2. co2Direction compares ").append(last).append(" with ").append(baseYear).append(" (")
                .append(format(lastCo2Mt)).append(" Mt): \"increasing\" if more than 2% higher, \"decreasing\" if more ")
                .append("than 2% lower, otherwise \"stable\".\n")
                .append("3. Base the projection on the historical trend. If you depart from it, explain why in the summary ")
                .append("using only facts visible in the table.\n")
                .append("4. summary: 3 to 6 sentences.\n")
                .append("5. keyDrivers: 1 to 5 short phrases grounded in the table.\n")
                .append("6. risks: 1 to 5 short phrases about risks to Paris Agreement alignment.\n")
                .append("7. confidence: \"low\", \"medium\" or \"high\"; lower it when the data is volatile or sparse.\n")
                .append("Respond with JSON only, matching the provided schema.\n");
        return p.toString();
    }

    /** Appended on the single retry so the model can fix exactly what failed. */
    public static String repairSuffix(List<String> errors) {
        return "\nYour previous response was rejected for these reasons:\n- " + String.join("\n- ", errors)
                + "\nReturn a corrected JSON response that fixes every one of them.\n";
    }

    private static String format(double v) {
        return String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
