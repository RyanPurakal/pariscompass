package com.ryanpurakal.pariscompass.service;

import com.ryanpurakal.pariscompass.domain.Country;
import com.ryanpurakal.pariscompass.etl.MetricDefinition;
import com.ryanpurakal.pariscompass.exception.CountryNotFoundException;
import com.ryanpurakal.pariscompass.exception.InsufficientDataException;
import com.ryanpurakal.pariscompass.model.AlignmentResponse;
import com.ryanpurakal.pariscompass.model.ProjectionResponse;
import com.ryanpurakal.pariscompass.model.SeriesPoint;
import com.ryanpurakal.pariscompass.projection.ProjectionModel;
import com.ryanpurakal.pariscompass.projection.ProjectionOutput;
import com.ryanpurakal.pariscompass.projection.ProjectionPromptBuilder;
import com.ryanpurakal.pariscompass.projection.ProjectionSchema;
import com.ryanpurakal.pariscompass.projection.ProjectionValidator;
import com.ryanpurakal.pariscompass.projection.TrendExtrapolator;
import com.ryanpurakal.pariscompass.repository.AnalyticsRepository;
import com.ryanpurakal.pariscompass.repository.CountryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Produces a five-year CO2 projection for a country:
 * load history, compute the alignment score (deterministic), build a grounded prompt, call the model,
 * validate, retry once with the validation errors, and otherwise fall back to trend extrapolation.
 * The response always says which path produced it.
 */
@Slf4j
@Service
public class ProjectionService {
    static final int MAX_ATTEMPTS = 2;
    static final int MIN_WINDOW_POINTS = 6;
    static final int WINDOW_YEARS = 10;

    private final CountryRepository countries;
    private final AnalyticsRepository analytics;
    private final AlignmentService alignmentService;
    private final ProjectionSchema schema;
    private final ProjectionValidator validator;
    private final ProjectionModel model;
    private final Clock clock;

    public ProjectionService(CountryRepository countries, AnalyticsRepository analytics, AlignmentService alignmentService,
                             ProjectionSchema schema, ProjectionValidator validator,
                             ObjectProvider<ProjectionModel> model, Clock clock) {
        this.countries = countries;
        this.analytics = analytics;
        this.alignmentService = alignmentService;
        this.schema = schema;
        this.validator = validator;
        this.model = model.getIfAvailable();
        this.clock = clock;
        if (this.model == null) {
            log.warn("No AI model configured (GEMINI_API_KEY unset): projections use trend extrapolation");
        }
    }

    public ProjectionResponse project(String iso3) {
        ProjectionInput input = prepare(iso3);
        return generate(input);
    }

    /** Everything the projection depends on, gathered before any model call. */
    ProjectionInput prepare(String iso3) {
        Country country = countries.findById(iso3).orElseThrow(() -> new CountryNotFoundException(iso3));
        List<String> codes = ProjectionPromptBuilder.COLUMNS_CODES;
        Map<String, List<SeriesPoint>> history = analytics.findSeries(iso3, codes, 1750, 2100).stream()
                .collect(Collectors.groupingBy(AnalyticsRepository.MetricPoint::getMetricCode,
                        Collectors.mapping(p -> new SeriesPoint(p.getYear(), p.getValue()), Collectors.toList())));

        List<SeriesPoint> co2 = history.getOrDefault(MetricDefinition.CO2_TOTAL_MT.code(), List.of());
        SeriesPoint last = co2.stream().max(Comparator.comparingInt(SeriesPoint::year))
                .orElseThrow(() -> new InsufficientDataException("No CO2 emissions data for " + iso3));
        List<SeriesPoint> window = co2.stream()
                .filter(p -> p.year() > last.year() - WINDOW_YEARS && p.value() > 0).toList();
        if (last.value() <= 0 || window.size() < MIN_WINDOW_POINTS) {
            throw new InsufficientDataException("A projection needs positive CO2 data for at least " + MIN_WINDOW_POINTS
                    + " of the last " + WINDOW_YEARS + " years; " + iso3 + " has " + window.size());
        }
        AlignmentResponse alignment = alignmentService.score(iso3);
        String prompt = ProjectionPromptBuilder.build(iso3, country.getName(), history, last.year(), last.value(), alignment);
        return new ProjectionInput(iso3, country.getName(), last.year(), last.value(), window, alignment, prompt);
    }

    ProjectionResponse generate(ProjectionInput in) {
        long start = System.nanoTime();
        if (model == null) {
            return fallback(in, "AI model not configured", List.of(), 0, start);
        }
        String prompt = in.prompt();
        List<String> errors = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            ProjectionModel.Reply reply;
            try {
                reply = model.generate(prompt, schema.asMap());
            } catch (RuntimeException e) {
                log.warn("{}: model call failed on attempt {}", in.iso3(), attempt, e);
                return fallback(in, "model call failed (" + e.getClass().getSimpleName() + ")", errors, attempt, start);
            }
            ProjectionValidator.Result result = validator.validate(reply.text(), in.baseYear(), in.baseYearCo2Mt());
            if (result.valid()) {
                log.info("{}: model projection valid on attempt {} (tokens in={}, out={})",
                        in.iso3(), attempt, reply.promptTokens(), reply.outputTokens());
                return response(in, "model", model.name(), attempt == 1 ? "VALID" : "REPAIRED", attempt, null,
                        List.of(), result.output(), start);
            }
            errors = result.errors();
            log.warn("{}: model output rejected on attempt {}: {}", in.iso3(), attempt, errors);
            prompt = in.prompt() + ProjectionPromptBuilder.repairSuffix(errors);
        }
        return fallback(in, "model output failed validation " + MAX_ATTEMPTS + " times", errors, MAX_ATTEMPTS, start);
    }

    private ProjectionResponse fallback(ProjectionInput in, String reason, List<String> errors, int attempts, long start) {
        ProjectionOutput output = TrendExtrapolator.extrapolate(in.window(), in.baseYear(), in.baseYearCo2Mt());
        return response(in, "trend-extrapolation", model == null ? null : model.name(), "FALLBACK", attempts, reason,
                errors, output, start);
    }

    private ProjectionResponse response(ProjectionInput in, String generatedBy, String modelName, String status,
                                        int attempts, String fallbackReason, List<String> errors,
                                        ProjectionOutput output, long start) {
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return new ProjectionResponse(null, in.iso3(), in.name(), generatedBy, modelName,
                ProjectionPromptBuilder.PROMPT_VERSION, status, attempts, fallbackReason, errors,
                Instant.now(clock), false, latencyMs, in.baseYear(), in.baseYearCo2Mt(),
                in.alignment().score(), in.alignment().band(), output);
    }

    record ProjectionInput(String iso3, String name, int baseYear, double baseYearCo2Mt, List<SeriesPoint> window,
                           AlignmentResponse alignment, String prompt) {
    }
}
