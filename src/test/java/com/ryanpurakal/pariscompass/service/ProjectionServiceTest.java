package com.ryanpurakal.pariscompass.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryanpurakal.pariscompass.config.AppProperties;
import com.ryanpurakal.pariscompass.domain.Country;
import com.ryanpurakal.pariscompass.repository.ProjectionRecordRepository;
import com.ryanpurakal.pariscompass.exception.InsufficientDataException;
import com.ryanpurakal.pariscompass.model.AlignmentResponse;
import com.ryanpurakal.pariscompass.model.ProjectionResponse;
import com.ryanpurakal.pariscompass.projection.ProjectionModel;
import com.ryanpurakal.pariscompass.projection.ProjectionSchema;
import com.ryanpurakal.pariscompass.projection.ProjectionValidator;
import com.ryanpurakal.pariscompass.repository.AnalyticsRepository;
import com.ryanpurakal.pariscompass.repository.CountryRepository;
import com.ryanpurakal.pariscompass.scoring.AlignmentScorer;
import com.ryanpurakal.pariscompass.support.FakeProjectionModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Orchestration: model success, repair after one bad reply, fallback paths, and the data precondition. */
class ProjectionServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);

    private final CountryRepository countries = mock(CountryRepository.class);
    private final AnalyticsRepository analytics = mock(AnalyticsRepository.class);
    private final AlignmentService alignment = mock(AlignmentService.class);
    private final FakeProjectionModel fake = new FakeProjectionModel();
    private final ProjectionRecordRepository records = mock(ProjectionRecordRepository.class);

    record Point(String getMetricCode, int getYear, double getValue) implements AnalyticsRepository.MetricPoint {
    }

    @BeforeEach
    void setUp() {
        Country usa = mock(Country.class);
        when(usa.getIso3()).thenReturn("USA");
        when(usa.getName()).thenReturn("United States");
        when(countries.findById("USA")).thenReturn(Optional.of(usa));
        // CO2 falling 1% a year from 1000 Mt in 2015 to about 913.5 Mt in 2024.
        List<AnalyticsRepository.MetricPoint> co2 = IntStream.rangeClosed(2015, 2024)
                .mapToObj(y -> (AnalyticsRepository.MetricPoint) new Point("co2_total_mt", y, 1000 * Math.pow(0.99, y - 2015)))
                .toList();
        when(analytics.findSeries(eq("USA"), any(), anyInt(), anyInt())).thenReturn(co2);
        when(records.findReusable(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(records.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(alignment.score("USA")).thenReturn(new AlignmentResponse("USA", "United States", "v1", 30.6,
                AlignmentScorer.Band.LOW, List.of(), null, "d"));
    }

    private ProjectionService service(ProjectionModel model) {
        StaticListableBeanFactory factory = model == null
                ? new StaticListableBeanFactory() : new StaticListableBeanFactory(Map.of("model", model));
        ProjectionSchema schema = new ProjectionSchema(MAPPER);
        return new ProjectionService(countries, analytics, alignment, schema, new ProjectionValidator(MAPPER, schema),
                factory.getBeanProvider(ProjectionModel.class), CLOCK, records,
                new AppProperties(new AppProperties.Cors(List.of("http://localhost:5173")),
                        new AppProperties.Gemini("m", "", false), null, null, null));
    }

    private static String validReply(String direction, double... values) {
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            pts.append(i > 0 ? "," : "").append("{\"year\":").append(2025 + i).append(",\"co2Mt\":").append(values[i]).append('}');
        }
        return """
                {"summary": "Emissions have declined about one percent a year and are projected to keep doing so.",
                 "co2Direction": "%s", "projectedCo2Mt": [%s],
                 "keyDrivers": ["Steady decline"], "risks": ["Policy reversal"], "confidence": "medium"}
                """.formatted(direction, pts);
    }

    @Test
    void validFirstReplyIsReturnedAsModelOutput() {
        fake.reply(validReply("decreasing", 905, 896, 887, 878, 870));
        ProjectionResponse r = service(fake).project("USA");

        assertThat(r.generatedBy()).isEqualTo("model");
        assertThat(r.status()).isEqualTo("VALID");
        assertThat(r.attempts()).isEqualTo(1);
        assertThat(r.model()).isEqualTo("fake-model");
        assertThat(r.promptVersion()).isEqualTo("p1");
        assertThat(r.baseYear()).isEqualTo(2024);
        assertThat(r.alignmentScore()).isEqualTo(30.6);
        assertThat(r.projection().projectedCo2Mt()).hasSize(5);
        assertThat(fake.prompts()).hasSize(1);
        assertThat(fake.prompts().get(0)).contains("United States (USA)", "30.6/100");
    }

    @Test
    void invalidReplyIsRetriedOnceWithTheErrors() {
        fake.reply(validReply("increasing", 905, 896, 887, 878, 870))
                .reply(validReply("decreasing", 905, 896, 887, 878, 870));
        ProjectionResponse r = service(fake).project("USA");

        assertThat(r.status()).isEqualTo("REPAIRED");
        assertThat(r.attempts()).isEqualTo(2);
        assertThat(fake.prompts().get(1)).contains("previous response was rejected")
                .contains("co2Direction is 'increasing' but the numbers say 'decreasing'");
    }

    @Test
    void twoInvalidRepliesFallBackToTrendExtrapolation() {
        fake.reply("not json").reply(validReply("decreasing", 1, 1, 1, 1, 1));
        ProjectionResponse r = service(fake).project("USA");

        assertThat(r.generatedBy()).isEqualTo("trend-extrapolation");
        assertThat(r.status()).isEqualTo("FALLBACK");
        assertThat(r.attempts()).isEqualTo(2);
        assertThat(r.fallbackReason()).isEqualTo("model output failed validation 2 times");
        assertThat(r.validationErrors()).isNotEmpty().allMatch(e -> e.contains("outside 50%-150%"));
        assertThat(r.projection().co2Direction()).isEqualTo("decreasing");
    }

    @Test
    void modelExceptionFallsBackWithoutRetrying() {
        fake.fail(new RuntimeException("429 quota exceeded for key AIzaSecret"));
        ProjectionResponse r = service(fake).project("USA");

        assertThat(r.status()).isEqualTo("FALLBACK");
        assertThat(r.fallbackReason()).isEqualTo("model call failed (RuntimeException)").doesNotContain("AIza");
        assertThat(fake.prompts()).hasSize(1);
    }

    @Test
    void noModelConfiguredFallsBackImmediately() {
        ProjectionResponse r = service(null).project("USA");

        assertThat(r.status()).isEqualTo("FALLBACK");
        assertThat(r.attempts()).isZero();
        assertThat(r.model()).isNull();
        assertThat(r.fallbackReason()).isEqualTo("AI model not configured");
    }

    @Test
    void tooLittleHistoryIsInsufficientData() {
        when(analytics.findSeries(eq("USA"), any(), anyInt(), anyInt())).thenReturn(List.of(
                new Point("co2_total_mt", 2023, 900), new Point("co2_total_mt", 2024, 890)));
        assertThatThrownBy(() -> service(fake).project("USA"))
                .isInstanceOf(InsufficientDataException.class)
                .hasMessageContaining("has 2");
    }
}
