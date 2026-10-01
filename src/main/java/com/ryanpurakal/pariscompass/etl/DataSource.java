package com.ryanpurakal.pariscompass.etl;

/**
 * Upstream datasets and the CSV columns that identify a row in each.
 * The download location is configuration (app.etl.sources), not code.
 */
public enum DataSource {
    OWID_CO2(
            "Our World in Data: CO2 and Greenhouse Gas Emissions",
            "https://github.com/owid/co2-data",
            "Global Carbon Project; Jones et al. (2024); Energy Institute; via Our World in Data",
            "iso_code", "country", "year"),
    OWID_ENERGY(
            "Our World in Data: Energy",
            "https://github.com/owid/energy-data",
            "Ember (2025); Energy Institute Statistical Review of World Energy (2025); via Our World in Data",
            "iso_code", "country", "year"),
    OWID_TEMPERATURE(
            "Our World in Data: Annual temperature anomalies (Copernicus ERA5)",
            "https://ourworldindata.org/grapher/annual-temperature-anomalies",
            "Contains modified Copernicus Climate Change Service information; with major processing by Our World in Data",
            "code", "entity", "year");

    private final String displayName;
    private final String homepage;
    private final String citation;
    private final String isoColumn;
    private final String nameColumn;
    private final String yearColumn;

    DataSource(String displayName, String homepage, String citation,
               String isoColumn, String nameColumn, String yearColumn) {
        this.displayName = displayName;
        this.homepage = homepage;
        this.citation = citation;
        this.isoColumn = isoColumn;
        this.nameColumn = nameColumn;
        this.yearColumn = yearColumn;
    }

    public String displayName() { return displayName; }
    public String homepage() { return homepage; }
    public String citation() { return citation; }
    public String isoColumn() { return isoColumn; }
    public String nameColumn() { return nameColumn; }
    public String yearColumn() { return yearColumn; }
}
