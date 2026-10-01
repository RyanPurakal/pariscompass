package com.ryanpurakal.pariscompass.service;

import com.google.genai.Client;
import com.ryanpurakal.pariscompass.config.AppProperties;
import com.ryanpurakal.pariscompass.exception.ProjectionUnavailableException;
import com.ryanpurakal.pariscompass.model.CountryMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiServiceTest {

    @Test
    void withoutApiKeyProjectionIsUnavailable() {
        AppProperties props = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Gemini("gemini-2.5-flash", "", false), null);
        GeminiService service = new GeminiService(new StaticListableBeanFactory().getBeanProvider(Client.class), props);

        CountryMetrics metrics = CountryMetrics.builder().iso3("USA").name("United States").build();

        assertThatThrownBy(() -> service.generateProjection("USA", metrics))
                .isInstanceOf(ProjectionUnavailableException.class)
                .hasMessageContaining("not configured");
    }
}
