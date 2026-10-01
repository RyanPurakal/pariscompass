package com.ryanpurakal.pariscompass.service;

import com.ryanpurakal.pariscompass.config.AppProperties;
import com.ryanpurakal.pariscompass.etl.DataSource;
import com.ryanpurakal.pariscompass.etl.EtlRepository;
import com.ryanpurakal.pariscompass.etl.EtlService;
import com.ryanpurakal.pariscompass.etl.SourceFetcher;
import com.ryanpurakal.pariscompass.exception.CountryNotFoundException;
import com.ryanpurakal.pariscompass.model.CountryInfo;
import com.ryanpurakal.pariscompass.model.CountryMetrics;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reads through JPA what the ETL wrote with JDBC, against a real Postgres. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CountryMetricsServiceIntegrationTest {

    @Autowired
    private CountryMetricsService service;
    @Autowired
    private EtlRepository etlRepository;
    @Autowired
    private SourceFetcher fetcher;
    @Autowired
    private AppProperties properties;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void loadFixtures() {
        jdbc.execute("TRUNCATE etl_rejection, etl_source_result, observation, etl_run, country, metric, data_source CASCADE");
        AppProperties props = new AppProperties(properties.cors(), properties.gemini(), new AppProperties.Etl(false, false, true,
                Map.of(DataSource.OWID_CO2, "classpath:etl/co2.csv",
                        DataSource.OWID_ENERGY, "classpath:etl/energy.csv",
                        DataSource.OWID_TEMPERATURE, "classpath:etl/temperature.csv")));
        new EtlService(etlRepository, fetcher, props,
                Clock.fixed(Instant.parse("2025-06-01T00:00:00Z"), ZoneOffset.UTC)).runAll();
    }

    @Test
    void latestMetricsUseEachMetricsOwnLatestYear() {
        CountryMetrics usa = service.getLatestMetrics("USA");

        assertThat(usa.getName()).isEqualTo("United States");
        assertThat(usa.getCo2TotalMt()).isEqualTo(4904.12);
        assertThat(usa.getCo2PerCapita()).isEqualTo(14.197);
        assertThat(usa.getRenewablesSharePct()).isEqualTo(22.35);
        assertThat(usa.getTemperatureAnomalyC()).isEqualTo(0.83337736);
        assertThat(usa.getYears()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "co2PerCapita", 2024, "co2TotalMt", 2024, "renewablesSharePct", 2024, "temperatureAnomalyC", 2025));
        assertThat(usa.getSource().getTemp()).contains("Copernicus ERA5");
    }

    @Test
    void missingMetricsAreNullAndHaveNoYear() {
        CountryMetrics nor = service.getLatestMetrics("NOR");

        assertThat(nor.getTemperatureAnomalyC()).isEqualTo(-4.3122134);
        assertThat(nor.getCo2TotalMt()).isNull();
        assertThat(nor.getYears()).containsOnlyKeys("temperatureAnomalyC");
    }

    @Test
    void unknownCountryThrows() {
        assertThatThrownBy(() -> service.getLatestMetrics("ATA")).isInstanceOf(CountryNotFoundException.class);
    }

    @Test
    void countriesAreSortedByName() {
        assertThat(service.getAllCountries()).extracting(CountryInfo::getName).containsExactly(
                "Democratic Republic of Congo", "Germany", "Iceland", "Norway", "Sint Maarten (Dutch part)",
                "Uganda", "United States");
    }
}
