package com.ryanpurakal.pariscompass.etl;

import com.ryanpurakal.pariscompass.config.AppProperties;
import com.ryanpurakal.pariscompass.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the whole pipeline against a real Postgres (Testcontainers) using the fixture CSVs. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EtlIntegrationTest {
    private static final Clock CLOCK_2025 = Clock.fixed(Instant.parse("2025-06-01T00:00:00Z"), ZoneOffset.UTC);
    private static final Map<DataSource, String> FIXTURES = Map.of(
            DataSource.OWID_CO2, "classpath:etl/co2.csv",
            DataSource.OWID_ENERGY, "classpath:etl/energy.csv",
            DataSource.OWID_TEMPERATURE, "classpath:etl/temperature.csv");

    @Autowired
    private EtlRepository repository;
    @Autowired
    private SourceFetcher fetcher;
    @Autowired
    private AppProperties properties;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE etl_rejection, etl_source_result, observation, etl_run, country, metric, data_source CASCADE");
    }

    private EtlRunSummary run(Map<DataSource, String> sources, boolean force) {
        AppProperties props = new AppProperties(properties.cors(), properties.gemini(),
                new AppProperties.Etl(false, false, force, sources), properties.projection());
        return new EtlService(repository, fetcher, props, CLOCK_2025).runAll();
    }

    private static SourceResult result(EtlRunSummary s, DataSource source) {
        return s.sources().stream().filter(r -> r.source() == source).findFirst().orElseThrow();
    }

    @Test
    void firstRunInsertsValidatedValuesAndRecordsRejections() {
        EtlRunSummary summary = run(FIXTURES, false);

        assertThat(summary.succeeded()).isTrue();
        SourceResult co2 = result(summary, DataSource.OWID_CO2);
        assertThat(co2.rowsRead()).isEqualTo(11);
        assertThat(co2.valuesInserted()).isEqualTo(14);
        assertThat(co2.rowsRejected()).isEqualTo(4);
        assertThat(co2.valuesRejected()).isEqualTo(2);
        SourceResult energy = result(summary, DataSource.OWID_ENERGY);
        assertThat(energy.valuesInserted()).isEqualTo(12);
        assertThat(energy.nameMismatches()).isEqualTo(1);
        assertThat(result(summary, DataSource.OWID_TEMPERATURE).valuesInserted()).isEqualTo(3);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM observation", Integer.class)).isEqualTo(29);
        assertThat(jdbc.queryForList("SELECT iso3 FROM country ORDER BY iso3", String.class))
                .containsExactly("COD", "DEU", "ISL", "NOR", "SXM", "UGA", "USA");
        // First source to name a country wins; later disagreements are only counted.
        assertThat(jdbc.queryForObject("SELECT name FROM country WHERE iso3 = 'DEU'", String.class)).isEqualTo("Germany");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM etl_rejection WHERE run_id = ?", Integer.class,
                summary.runId())).isEqualTo(4 + 2 + 2 + 1);
        assertThat(jdbc.queryForObject("SELECT status FROM etl_run WHERE id = ?", String.class, summary.runId()))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM metric", Integer.class))
                .isEqualTo(MetricDefinition.values().length);
    }

    @Test
    void secondRunWithUnchangedFilesIsSkipped() {
        run(FIXTURES, false);
        EtlRunSummary second = run(FIXTURES, false);

        assertThat(second.sources()).allSatisfy(r -> assertThat(r.skippedUnchanged()).isTrue());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM observation", Integer.class)).isEqualTo(29);
    }

    @Test
    void forcedRerunIsIdempotent() {
        run(FIXTURES, false);
        EtlRunSummary forced = run(FIXTURES, true);

        assertThat(forced.sources()).allSatisfy(r -> {
            assertThat(r.skippedUnchanged()).isFalse();
            assertThat(r.valuesInserted()).isZero();
            assertThat(r.valuesUpdated()).isZero();
        });
        assertThat(forced.sources().stream().mapToInt(SourceResult::valuesUnchanged).sum()).isEqualTo(29);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM observation", Integer.class)).isEqualTo(29);
    }

    @Test
    void changedUpstreamValueIsUpdatedInPlace() {
        EtlRunSummary first = run(FIXTURES, false);
        Map<DataSource, String> changed = new java.util.HashMap<>(FIXTURES);
        changed.put(DataSource.OWID_CO2, "classpath:etl/co2-changed.csv");
        EtlRunSummary second = run(changed, false);

        SourceResult co2 = result(second, DataSource.OWID_CO2);
        assertThat(co2.valuesInserted()).isZero();
        assertThat(co2.valuesUpdated()).isEqualTo(1);
        assertThat(co2.valuesUnchanged()).isEqualTo(13);
        assertThat(result(second, DataSource.OWID_ENERGY).skippedUnchanged()).isTrue();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT value, etl_run_id FROM observation WHERE iso3 = 'USA' AND metric_code = 'co2_total_mt' AND year = 2024");
        assertThat(row.get("value")).isEqualTo(4905.0);
        assertThat(row.get("etl_run_id")).isEqualTo(second.runId());
        // Untouched rows keep the run that wrote them.
        assertThat(jdbc.queryForObject(
                "SELECT etl_run_id FROM observation WHERE iso3 = 'USA' AND metric_code = 'co2_total_mt' AND year = 2023",
                Long.class)).isEqualTo(first.runId());
    }

    @Test
    void brokenSourceFailsRunButOtherSourcesStillLoad() {
        Map<DataSource, String> sources = new java.util.HashMap<>(FIXTURES);
        sources.put(DataSource.OWID_ENERGY, "classpath:etl/does-not-exist.csv");
        EtlRunSummary summary = run(sources, false);

        assertThat(summary.succeeded()).isFalse();
        assertThat(result(summary, DataSource.OWID_ENERGY).failed()).isTrue();
        assertThat(result(summary, DataSource.OWID_CO2).valuesInserted()).isEqualTo(14);
        assertThat(result(summary, DataSource.OWID_TEMPERATURE).valuesInserted()).isEqualTo(3);
        List<String> status = jdbc.queryForList("SELECT status FROM etl_run WHERE id = ?", String.class, summary.runId());
        assertThat(status).containsExactly("FAILED");
    }
}
