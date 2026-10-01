package com.ryanpurakal.pariscompass.exception;

import com.ryanpurakal.pariscompass.controller.CountryController;
import com.ryanpurakal.pariscompass.model.CountryMetrics;
import com.ryanpurakal.pariscompass.service.AlignmentService;
import com.ryanpurakal.pariscompass.service.AnalyticsService;
import com.ryanpurakal.pariscompass.service.CountryMetricsService;
import com.ryanpurakal.pariscompass.service.ProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
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
    private ProjectionService projectionService;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private AlignmentService alignmentService;

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
    void insufficientDataReturns422Problem() throws Exception {
        CountryMetrics metrics = CountryMetrics.builder().iso3("TUV").name("Tuvalu").build();
        when(metricsService.getLatestMetrics("TUV")).thenReturn(metrics);
        when(projectionService.project("TUV")).thenThrow(new InsufficientDataException("not enough CO2 history"));

        mvc.perform(post("/api/countries/TUV/projection"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.detail").value("not enough CO2 history"));
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
