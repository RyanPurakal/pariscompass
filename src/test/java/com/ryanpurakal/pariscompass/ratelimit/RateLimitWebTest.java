package com.ryanpurakal.pariscompass.ratelimit;

import com.ryanpurakal.pariscompass.config.AppProperties;
import com.ryanpurakal.pariscompass.controller.CountryController;
import com.ryanpurakal.pariscompass.service.AlignmentService;
import com.ryanpurakal.pariscompass.service.AnalyticsService;
import com.ryanpurakal.pariscompass.service.CountryMetricsService;
import com.ryanpurakal.pariscompass.service.ProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CountryController.class)
@Import({ProjectionRateLimiter.class, RateLimitWebTest.PropertiesConfig.class})
@TestPropertySource(properties = {
        "app.rate-limit.projections-per-client-per-minute=2",
        "app.rate-limit.projections-per-hour-global=100"})
class RateLimitWebTest {

    /** @WebMvcTest slices skip @ConfigurationPropertiesScan; bind AppProperties so the real limiter gets its limits. */
    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class PropertiesConfig {
    }

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private CountryMetricsService metricsService;
    @MockitoBean
    private ProjectionService projectionService;
    @MockitoBean
    private AnalyticsService analyticsService;
    @MockitoBean
    private AlignmentService alignmentService;

    @Test
    void thirdProjectionInAMinuteGets429WithRetryAfter() throws Exception {
        mvc.perform(post("/api/countries/USA/projection"))
                .andExpect(status().isOk()).andExpect(header().string("X-RateLimit-Remaining", "1"));
        mvc.perform(post("/api/countries/USA/projection"))
                .andExpect(status().isOk()).andExpect(header().string("X-RateLimit-Remaining", "0"));
        mvc.perform(post("/api/countries/DEU/projection"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")));
    }

    @Test
    void otherClientsAndReadEndpointsAreNotLimited() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/countries/USA/projection").with(r -> { r.setRemoteAddr("10.0.0.9"); return r; }));
        }
        mvc.perform(post("/api/countries/USA/projection").with(r -> { r.setRemoteAddr("10.0.0.10"); return r; }))
                .andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/countries")).andExpect(status().isOk());
        }
    }
}
