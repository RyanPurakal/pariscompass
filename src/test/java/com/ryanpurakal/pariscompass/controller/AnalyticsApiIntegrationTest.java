package com.ryanpurakal.pariscompass.controller;

import com.ryanpurakal.pariscompass.config.AppProperties;
import com.ryanpurakal.pariscompass.etl.EtlRepository;
import com.ryanpurakal.pariscompass.etl.MetricDefinition;
import com.ryanpurakal.pariscompass.etl.SourceFetcher;
import com.ryanpurakal.pariscompass.support.FixtureData;
import com.ryanpurakal.pariscompass.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP to database, end to end: controllers, services, native queries, real Postgres, fixture data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AnalyticsApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EtlRepository etlRepository;
    @Autowired
    private SourceFetcher fetcher;
    @Autowired
    private AppProperties properties;

    @BeforeEach
    void loadFixtures() {
        FixtureData.reload(jdbc, etlRepository, fetcher, properties);
    }

    @Test
    void metricCatalogListsEveryMetricWithCoverage() throws Exception {
        mvc.perform(get("/api/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(MetricDefinition.values().length)))
                .andExpect(jsonPath("$[?(@.code=='co2_total_mt')].coverage.values").value(contains(4)))
                .andExpect(jsonPath("$[?(@.code=='co2_total_mt')].coverage.countries").value(contains(3)))
                .andExpect(jsonPath("$[?(@.code=='co2_total_mt')].coverage.firstYear").value(contains(1954)))
                .andExpect(jsonPath("$[?(@.code=='co2_total_mt')].coverage.defaultYear").value(contains(2024)))
                .andExpect(jsonPath("$[?(@.code=='co2_total_mt')].source.code").value(contains("OWID_CO2")));
    }

    @Test
    void seriesReturnsRequestedMetricsInYearOrderWithinRange() throws Exception {
        mvc.perform(get("/api/countries/usa/series")
                        .param("metrics", "co2_total_mt,temperature_anomaly_c")
                        .param("from", "2023").param("to", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iso3").value("USA"))
                .andExpect(jsonPath("$.series", hasSize(2)))
                .andExpect(jsonPath("$.series[0].metric").value("co2_total_mt"))
                .andExpect(jsonPath("$.series[0].points[*].year").value(contains(2023, 2024)))
                .andExpect(jsonPath("$.series[0].points[1].value").value(4904.12))
                .andExpect(jsonPath("$.series[1].metric").value("temperature_anomaly_c"))
                .andExpect(jsonPath("$.series[1].points[*].year").value(contains(2024)));
    }

    @Test
    void seriesWithoutMetricsReturnsAllMetricsIncludingEmptyOnes() throws Exception {
        mvc.perform(get("/api/countries/NOR/series"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series", hasSize(MetricDefinition.values().length)))
                .andExpect(jsonPath("$.series[?(@.metric=='co2_total_mt')].points[*]", hasSize(0)));
    }

    @Test
    void seriesRejectsUnknownMetricAndInvertedRange() throws Exception {
        mvc.perform(get("/api/countries/USA/series").param("metrics", "co2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_METRIC"));
        mvc.perform(get("/api/countries/USA/series").param("from", "2024").param("to", "2000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_YEAR_RANGE"));
        mvc.perform(get("/api/countries/USA/series").param("from", "1200"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void compareKeepsRequestedOrderAndDedupes() throws Exception {
        mvc.perform(get("/api/compare").param("countries", "deu,USA,DEU").param("metric", "renewables_share_elec_pct"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit").value("%"))
                .andExpect(jsonPath("$.countries[*].iso3").value(contains("DEU", "USA")))
                .andExpect(jsonPath("$.countries[0].points[0].value").value(59.1))
                .andExpect(jsonPath("$.countries[1].points[0].value").value(22.35));
    }

    @Test
    void compareRequiresTwoToFourKnownCountries() throws Exception {
        mvc.perform(get("/api/compare").param("countries", "USA").param("metric", "co2_total_mt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COUNTRY_LIST"));
        mvc.perform(get("/api/compare").param("countries", "USA,DEU,NOR,ISL,UGA").param("metric", "co2_total_mt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COUNTRY_LIST"));
        mvc.perform(get("/api/compare").param("countries", "USA,ATA").param("metric", "co2_total_mt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUNTRY_NOT_FOUND"));
    }

    @Test
    void rankingsDefaultToWellCoveredYearAndSortDescending() throws Exception {
        mvc.perform(get("/api/rankings").param("metric", "co2_total_mt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.order").value("desc"))
                .andExpect(jsonPath("$.countriesWithData").value(2))
                .andExpect(jsonPath("$.entries[*].iso3").value(contains("USA", "DEU")))
                .andExpect(jsonPath("$.entries[*].rank").value(contains(1, 2)));
    }

    @Test
    void rankingsAscendingWithLimitKeepsTotal() throws Exception {
        mvc.perform(get("/api/rankings").param("metric", "co2_total_mt").param("year", "2024")
                        .param("order", "asc").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].iso3").value("DEU"))
                .andExpect(jsonPath("$.countriesWithData").value(2));
    }

    @Test
    void rankingsRejectBadOrderAndLimit() throws Exception {
        mvc.perform(get("/api/rankings").param("metric", "co2_total_mt").param("order", "up"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER"));
        mvc.perform(get("/api/rankings").param("metric", "co2_total_mt").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rankingsForYearWithoutDataAreEmpty() throws Exception {
        mvc.perform(get("/api/rankings").param("metric", "co2_total_mt").param("year", "1800"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countriesWithData").value(0))
                .andExpect(jsonPath("$.entries", hasSize(0)));
    }
}
