package com.ryanpurakal.pariscompass.controller;

import com.ryanpurakal.pariscompass.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fails if an endpoint disappears from the published API document. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiDocsIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void apiDocumentListsEveryEndpoint() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Paris Compass API"))
                .andExpect(jsonPath("$.paths", hasKey("/api/countries")))
                .andExpect(jsonPath("$.paths", hasKey("/api/countries/{iso3}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/countries/{iso3}/series")))
                .andExpect(jsonPath("$.paths", hasKey("/api/countries/{iso3}/alignment")))
                .andExpect(jsonPath("$.paths", hasKey("/api/countries/{iso3}/projection")))
                .andExpect(jsonPath("$.paths", hasKey("/api/countries/{iso3}/projections")))
                .andExpect(jsonPath("$.paths", hasKey("/api/metrics")))
                .andExpect(jsonPath("$.paths", hasKey("/api/rankings")))
                .andExpect(jsonPath("$.paths", hasKey("/api/compare")))
                .andExpect(jsonPath("$.paths['/api/countries/{iso3}/projection'].post.responses", hasKey("429")));
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
