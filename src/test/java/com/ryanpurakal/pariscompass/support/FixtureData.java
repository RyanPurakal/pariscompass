package com.ryanpurakal.pariscompass.support;

import com.ryanpurakal.pariscompass.config.AppProperties;
import com.ryanpurakal.pariscompass.etl.DataSource;
import com.ryanpurakal.pariscompass.etl.EtlRepository;
import com.ryanpurakal.pariscompass.etl.EtlRunSummary;
import com.ryanpurakal.pariscompass.etl.EtlService;
import com.ryanpurakal.pariscompass.etl.SourceFetcher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

/** Resets the database and loads the fixture CSVs through the real ETL, so read tests see realistic data. */
public final class FixtureData {
    public static final Clock CLOCK_2025 = Clock.fixed(Instant.parse("2025-06-01T00:00:00Z"), ZoneOffset.UTC);
    public static final Map<DataSource, String> SOURCES = Map.of(
            DataSource.OWID_CO2, "classpath:etl/co2.csv",
            DataSource.OWID_ENERGY, "classpath:etl/energy.csv",
            DataSource.OWID_TEMPERATURE, "classpath:etl/temperature.csv");

    private FixtureData() {
    }

    public static void truncate(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE etl_rejection, etl_source_result, observation, etl_run, country, metric, data_source CASCADE");
    }

    public static EtlRunSummary reload(JdbcTemplate jdbc, EtlRepository repository, SourceFetcher fetcher,
                                       AppProperties properties) {
        truncate(jdbc);
        AppProperties props = new AppProperties(properties.cors(), properties.gemini(),
                new AppProperties.Etl(false, false, true, SOURCES), properties.projection(), properties.rateLimit());
        return new EtlService(repository, fetcher, props, CLOCK_2025).runAll();
    }
}
