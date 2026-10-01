package com.ryanpurakal.pariscompass.etl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Every metric the app stores: where it comes from, its unit, and the range a value must fall
 * in to be accepted. Ranges are physical plausibility bounds chosen up front, not fitted to the
 * data, so the ETL reports genuine outliers instead of hiding them.
 */
public enum MetricDefinition {
    CO2_TOTAL_MT("co2_total_mt", DataSource.OWID_CO2, "co2",
            "CO2 emissions", "Mt CO2", 0, 50_000,
            "Annual territorial CO2 emissions from fossil fuels and industry, excluding land-use change."),
    CO2_PER_CAPITA_T("co2_per_capita_t", DataSource.OWID_CO2, "co2_per_capita",
            "CO2 emissions per capita", "t CO2/person", 0, 100,
            "Annual territorial CO2 emissions divided by population."),
    GHG_TOTAL_MT("ghg_total_mt", DataSource.OWID_CO2, "total_ghg",
            "Greenhouse gas emissions", "Mt CO2e", -5_000, 50_000,
            "All greenhouse gases including land-use change and forestry. Can be negative for net sinks."),
    POPULATION("population", DataSource.OWID_CO2, "population",
            "Population", "people", 0, 10_000_000_000d,
            "Total population."),
    RENEWABLES_SHARE_ELEC_PCT("renewables_share_elec_pct", DataSource.OWID_ENERGY, "renewables_share_elec",
            "Renewables share of electricity", "%", 0, 100,
            "Share of electricity generation from renewables (hydro, solar, wind, bioenergy, other)."),
    LOW_CARBON_SHARE_ELEC_PCT("low_carbon_share_elec_pct", DataSource.OWID_ENERGY, "low_carbon_share_elec",
            "Low-carbon share of electricity", "%", 0, 100,
            "Share of electricity generation from renewables plus nuclear."),
    RENEWABLES_SHARE_ENERGY_PCT("renewables_share_energy_pct", DataSource.OWID_ENERGY, "renewables_share_energy",
            "Renewables share of primary energy", "%", 0, 100,
            "Share of all primary energy (not just electricity) from renewables. Covers far fewer countries."),
    TEMPERATURE_ANOMALY_C("temperature_anomaly_c", DataSource.OWID_TEMPERATURE, "temperature_anomaly",
            "Temperature anomaly", "°C", -10, 10,
            "Annual mean surface air temperature minus the 1991-2020 mean (Copernicus ERA5).");

    private final String code;
    private final DataSource source;
    private final String sourceColumn;
    private final String displayName;
    private final String unit;
    private final double min;
    private final double max;
    private final String description;

    MetricDefinition(String code, DataSource source, String sourceColumn, String displayName, String unit,
                     double min, double max, String description) {
        this.code = code;
        this.source = source;
        this.sourceColumn = sourceColumn;
        this.displayName = displayName;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.description = description;
    }

    public static Optional<MetricDefinition> byCode(String code) {
        return Arrays.stream(values()).filter(m -> m.code.equals(code)).findFirst();
    }

    public static List<MetricDefinition> forSource(DataSource source) {
        return Arrays.stream(values()).filter(m -> m.source == source).toList();
    }

    public boolean inRange(double value) {
        return value >= min && value <= max;
    }

    public String code() { return code; }
    public DataSource source() { return source; }
    public String sourceColumn() { return sourceColumn; }
    public String displayName() { return displayName; }
    public String unit() { return unit; }
    public double min() { return min; }
    public double max() { return max; }
    public String description() { return description; }
}
