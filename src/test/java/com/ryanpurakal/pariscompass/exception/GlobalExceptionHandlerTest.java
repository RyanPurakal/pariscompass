package com.ryanpurakal.pariscompass.exception;

import com.ryanpurakal.pariscompass.controller.CountryController;
import com.ryanpurakal.pariscompass.model.CountryMetrics;
import com.ryanpurakal.pariscompass.service.CountryMetricsService;
import com.ryanpurakal.pariscompass.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies every error path returns the same problem+json shape. */
@WebMvcTest(CountryController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CountryMetricsService metricsService;

    @MockitoBean
    private GeminiService geminiService;

    @Test
    void unknownCountryReturns404Problem() throws Exception {
        when(metricsService.getLatestMetrics("XXX")).thenThrow(new CountryNotFoundException("XXX"));

        mvc.perform(get("/api/countries/xxx"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.code").value("COUNTRY_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No country found with ISO3 code 'XXX'"))
                .andExpect(jsonPath("$.instance").value("/api/countries/xxx"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void malformedIso3Returns400Problem() throws Exception {
        mvc.perform(get("/api/countries/US1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail", containsString("ISO 3166-1 alpha-3")));
    }

    @Test
    void projectionFailureReturns503WithoutLeakingCause() throws Exception {
        CountryMetrics metrics = CountryMetrics.builder().iso3("USA").name("United States").build();
        when(metricsService.getLatestMetrics("USA")).thenReturn(metrics);
        when(geminiService.generateProjection(eq("USA"), any())).thenThrow(new ProjectionUnavailableException(
                "The projection service is temporarily unavailable. Try again later.",
                new RuntimeException("upstream 429: quota exceeded for key abc123")));

        mvc.perform(post("/api/countries/USA/projection"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PROJECTION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("abc123"))));
    }

    @Test
    void unexpectedExceptionReturnsGeneric500() throws Exception {
        when(metricsService.getAllCountries()).thenThrow(new IllegalStateException("jdbc:postgresql://secret-host"));

        mvc.perform(get("/api/countries"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("secret-host"))));
    }

    @Test
    void unknownRouteReturns404Problem() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethodReturns405Problem() throws Exception {
        mvc.perform(get("/api/countries/USA/projection"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
