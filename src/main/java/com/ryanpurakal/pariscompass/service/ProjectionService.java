package com.ryanpurakal.pariscompass.service;

import com.ryanpurakal.pariscompass.domain.Country;
import com.ryanpurakal.pariscompass.config.AppProperties;
import com.ryanpurakal.pariscompass.domain.ProjectionRecord;
import com.ryanpurakal.pariscompass.repository.ProjectionRecordRepository;
import com.ryanpurakal.pariscompass.scoring.AlignmentScorer;
import org.springframework.data.domain.Limit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
 *
 * Every result is stored. Before calling the model, the newest reusable stored result for the same
 * input hash and model (within the TTL) is returned instead. Concurrent requests for the same inputs
 * share one in-flight generation, so a burst of clicks causes one model call, not several. That
 * coalescing is per JVM; several instances would need a shared lock (e.g. a Postgres advisory lock).
 * No database connection is held while the model runs: reads and the final insert are separate,
 * short transactions.
 */
@Slf4j
@Service
public class ProjectionService {
    static final int MAX_ATTEMPTS = 2;
    static final int MIN_WINDOW_POINTS = 6;
    static final int WINDOW_YEARS = 10;
    static final long AWAIT_SECONDS = 90;

    private final CountryRepository countries;
    private final AnalyticsRepository analytics;
    private final AlignmentService alignmentService;
    private final ProjectionSchema schema;
    private final ProjectionValidator validator;
    private final ProjectionModel model;
    private final Clock clock;
    private final ProjectionRecordRepository records;
    private final Duration cacheTtl;
    private final ConcurrentHashMap<String, CompletableFuture<ProjectionResponse>> inFlight = new ConcurrentHashMap<>();

    public ProjectionService(CountryRepository countries, AnalyticsRepository analytics, AlignmentService alignmentService,
                             ProjectionSchema schema, ProjectionValidator validator,
                             ObjectProvider<ProjectionModel> model, Clock clock,
                             ProjectionRecordRepository records, AppProperties properties) {
        this.countries = countries;
        this.analytics = analytics;
        this.alignmentService = alignmentService;
        this.schema = schema;
        this.validator = validator;
        this.model = model.getIfAvailable();
        this.clock = clock;
        this.records = records;
        this.cacheTtl = properties.projection().cacheTtl();
        if (this.model == null) {
            log.warn("No AI model configured (GEMINI_API_KEY unset): projections use trend extrapolation");
        }
    }

    public ProjectionResponse project(String iso3) {
        ProjectionInput input = prepare(iso3);
        String modelName = model == null ? null : model.name();
        Optional<ProjectionResponse> cached = findReusable(input, modelName);
        if (cached.isPresent()) {
            return cached.get();
        }
        String key = input.iso3() + ":" + input.inputHash() + ":" + modelName;
        CompletableFuture<ProjectionResponse> mine = new CompletableFuture<>();
        CompletableFuture<ProjectionResponse> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            log.info("{}: joining in-flight projection", iso3);
            return await(running).asCached();
        }
        try {
            // Another request may have finished and stored a result between the lookup and putIfAbsent.
            ProjectionResponse result = findReusable(input, modelName).orElseGet(() -> store(input, generate(input)));
            mine.complete(result);
            return result;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    public List<ProjectionResponse> history(String iso3, int limit) {
        countries.findById(iso3).orElseThrow(() -> new CountryNotFoundException(iso3));
        return records.findByIso3OrderByCreatedAtDesc(iso3, Limit.of(limit)).stream()
                .map(r -> toResponse(r, false)).toList();
    }

    private Optional<ProjectionResponse> findReusable(ProjectionInput in, String modelName) {
        return records.findReusable(in.iso3(), in.inputHash(), modelName, Instant.now(clock).minus(cacheTtl), Limit.of(1))
                .stream().findFirst().map(r -> toResponse(r, true));
    }

    private ProjectionResponse store(ProjectionInput in, ProjectionResponse r) {
        ProjectionRecord saved = records.save(ProjectionRecord.builder()
                .iso3(r.iso3()).createdAt(r.generatedAt()).generatedBy(r.generatedBy()).model(r.model())
                .promptVersion(r.promptVersion()).inputHash(in.inputHash()).status(r.status())
                .attempts((short) r.attempts()).fallbackReason(r.fallbackReason()).validationErrors(r.validationErrors())
                .latencyMs((int) r.latencyMs()).baseYear((short) r.baseYear()).baseYearCo2Mt(r.baseYearCo2Mt())
                .alignmentScore(r.alignmentScore()).alignmentBand(r.alignmentBand() == null ? null : r.alignmentBand().name())
                .output(r.projection())
                .build());
        return toResponse(saved, false);
    }

    private ProjectionResponse toResponse(ProjectionRecord r, boolean cached) {
        return new ProjectionResponse(r.getId(), r.getIso3(), countryName(r.getIso3()), r.getGeneratedBy(), r.getModel(),
                r.getPromptVersion(), r.getStatus(), r.getAttempts(), r.getFallbackReason(), r.getValidationErrors(),
                r.getCreatedAt(), cached, r.getLatencyMs(), r.getBaseYear(), r.getBaseYearCo2Mt(), r.getAlignmentScore(),
                r.getAlignmentBand() == null ? null : AlignmentScorer.Band.valueOf(r.getAlignmentBand()), r.getOutput());
    }

    private String countryName(String iso3) {
        return countries.findById(iso3).map(Country::getName).orElse(iso3);
    }

    /** Waits for another request's generation: at most two model calls plus margin. */
    private static ProjectionResponse await(CompletableFuture<ProjectionResponse> running) {
        try {
            return running.orTimeout(AWAIT_SECONDS, TimeUnit.SECONDS).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
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
        String hash = sha256(ProjectionPromptBuilder.PROMPT_VERSION + "\n" + prompt);
        return new ProjectionInput(iso3, country.getName(), last.year(), last.value(), window, alignment, prompt, hash);
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

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    record ProjectionInput(String iso3, String name, int baseYear, double baseYearCo2Mt, List<SeriesPoint> window,
                           AlignmentResponse alignment, String prompt, String inputHash) {
    }
}
