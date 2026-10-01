package com.ryanpurakal.pariscompass.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryanpurakal.pariscompass.config.AppProperties;
import com.ryanpurakal.pariscompass.etl.EtlRepository;
import com.ryanpurakal.pariscompass.etl.EtlRunSummary;
import com.ryanpurakal.pariscompass.etl.SourceFetcher;
import com.ryanpurakal.pariscompass.projection.ProjectionModel;
import com.ryanpurakal.pariscompass.support.FakeProjectionModel;
import com.ryanpurakal.pariscompass.support.FixtureData;
import com.ryanpurakal.pariscompass.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Projection persistence and caching against real Postgres, with a scripted fake in place of Gemini. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ProjectionApiIntegrationTest.FakeModelConfig.class})
class ProjectionApiIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeModelConfig {
        @Bean
        FakeProjectionModel fakeProjectionModel() {
            return new FakeProjectionModel();
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EtlRepository etlRepository;
    @Autowired
    private SourceFetcher fetcher;
    @Autowired
    private AppProperties properties;
    @Autowired
    private FakeProjectionModel fake;
    @Autowired
    private List<ProjectionModel> models;

    /** France: CO2 falling 2% a year from 400 Mt in 2015, so 2024 is about 333.4 Mt. */
    @BeforeEach
    void setUp() {
        fake.reset();
        jdbc.execute("TRUNCATE projection");
        EtlRunSummary run = FixtureData.reload(jdbc, etlRepository, fetcher, properties);
        jdbc.update("INSERT INTO country (iso3, name) VALUES ('FRA', 'France')");
        for (int y = 2015; y <= 2024; y++) {
            jdbc.update("INSERT INTO observation (iso3, metric_code, year, value, etl_run_id) VALUES ('FRA', 'co2_total_mt', ?, ?, ?)",
                    y, 400 * Math.pow(0.98, y - 2015), run.runId());
        }
    }

    private static String reply() {
        return """
                {"summary": "Emissions have fallen about two percent a year and the projection continues that decline.",
                 "co2Direction": "decreasing",
                 "projectedCo2Mt": [{"year":2025,"co2Mt":327},{"year":2026,"co2Mt":320},{"year":2027,"co2Mt":314},
                                    {"year":2028,"co2Mt":308},{"year":2029,"co2Mt":301}],
                 "keyDrivers": ["Steady decline"], "risks": ["Slower progress"], "confidence": "medium"}
                """;
    }

    private JsonNode project() throws Exception {
        String body = mvc.perform(post("/api/countries/FRA/projection")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("projection");
    }

    private int storedCount() {
        return jdbc.queryForObject("SELECT count(*) FROM projection WHERE iso3 = 'FRA'", Integer.class);
    }

    @Test
    void fakeModelIsTheOnlyModelInThisContext() {
        assertThat(models).containsExactly(fake);
    }

    @Test
    void secondRequestIsServedFromStoredProjection() throws Exception {
        fake.reply(reply());
        JsonNode first = project();
        JsonNode second = project();

        assertThat(first.get("generatedBy").asText()).isEqualTo("model");
        assertThat(first.get("cached").asBoolean()).isFalse();
        assertThat(first.get("id").isNumber()).isTrue();
        assertThat(second.get("cached").asBoolean()).isTrue();
        assertThat(second.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(fake.prompts()).hasSize(1);
        assertThat(storedCount()).isEqualTo(1);
    }

    @Test
    void storedProjectionSurvivesAsJsonbAndIsListedInHistory() throws Exception {
        fake.reply(reply());
        project();
        String body = mvc.perform(get("/api/countries/fra/projections")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode history = mapper.readTree(body);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("model").asText()).isEqualTo("fake-model");
        assertThat(history.get(0).get("promptVersion").asText()).isEqualTo("p1");
        assertThat(history.get(0).get("projection").get("projectedCo2Mt")).hasSize(5);
        assertThat(jdbc.queryForObject("SELECT output->>'co2Direction' FROM projection", String.class)).isEqualTo("decreasing");
    }

    @Test
    void newDataChangesTheInputHashAndTriggersANewCall() throws Exception {
        fake.reply(reply()).reply(reply());
        project();
        jdbc.update("UPDATE observation SET value = value * 1.01 WHERE iso3 = 'FRA' AND year = 2024");
        JsonNode after = project();

        assertThat(after.get("cached").asBoolean()).isFalse();
        assertThat(fake.prompts()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT input_hash) FROM projection", Integer.class)).isEqualTo(2);
    }

    @Test
    void expiredEntryIsNotReused() throws Exception {
        fake.reply(reply()).reply(reply());
        project();
        jdbc.update("UPDATE projection SET created_at = created_at - (? || ' days')::interval",
                String.valueOf(properties.projection().cacheTtl().toDays() + 1));
        project();
        assertThat(fake.prompts()).hasSize(2);
    }

    @Test
    void fallbackAfterModelFailureIsStoredButNotReused() throws Exception {
        fake.fail(new RuntimeException("timeout")).reply(reply());
        JsonNode first = project();
        JsonNode second = project();

        assertThat(first.get("status").asText()).isEqualTo("FALLBACK");
        assertThat(second.get("status").asText()).isEqualTo("VALID");
        assertThat(second.get("cached").asBoolean()).isFalse();
        assertThat(storedCount()).isEqualTo(2);
    }

    @Test
    void concurrentRequestsShareOneModelCall() throws Exception {
        fake.replyAfter(reply(), 500);
        int clients = 5;
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        try {
            List<Callable<JsonNode>> calls = new ArrayList<>();
            for (int i = 0; i < clients; i++) {
                calls.add(this::project);
            }
            List<JsonNode> results = new ArrayList<>();
            for (Future<JsonNode> f : pool.invokeAll(calls)) {
                results.add(f.get());
            }
            assertThat(fake.prompts()).hasSize(1);
            assertThat(storedCount()).isEqualTo(1);
            assertThat(results).extracting(r -> r.get("id").asLong()).containsOnly(results.get(0).get("id").asLong());
            assertThat(results).filteredOn(r -> !r.get("cached").asBoolean()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void historyForUnknownCountryIs404() throws Exception {
        mvc.perform(get("/api/countries/ATA/projections")).andExpect(status().isNotFound());
    }
}
