package com.ryanpurakal.pariscompass.etl;

import com.ryanpurakal.pariscompass.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs the pipeline for every DataSource: fetch, skip if unchanged, parse and validate, write, record.
 * Each source commits on its own, so one broken upstream file does not block the others;
 * the run is marked FAILED if any source failed.
 * Re-running is safe: the upsert only touches values that changed.
 */
@Slf4j
@Service
public class EtlService {
    private static final Set<String> ISO3_CODES = Set.copyOf(Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA3));

    private final EtlRepository repository;
    private final SourceFetcher fetcher;
    private final AppProperties.Etl config;
    private final Clock clock;

    public EtlService(EtlRepository repository, SourceFetcher fetcher, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.fetcher = fetcher;
        this.config = properties.etl();
        this.clock = clock;
    }

    public EtlRunSummary runAll() {
        long start = System.currentTimeMillis();
        repository.syncCatalog();
        long runId = repository.startRun(config.force());
        log.info("ETL run {} started (force={})", runId, config.force());

        List<SourceResult> results = new ArrayList<>();
        for (DataSource source : DataSource.values()) {
            SourceResult result = runSource(runId, source);
            repository.recordSourceResult(runId, result);
            logResult(runId, result);
            results.add(result);
        }

        boolean succeeded = results.stream().noneMatch(SourceResult::failed);
        String error = succeeded ? null : results.stream().filter(SourceResult::failed)
                .map(r -> r.source() + ": " + r.error()).collect(Collectors.joining("; "));
        repository.finishRun(runId, succeeded, error);
        long duration = System.currentTimeMillis() - start;
        log.info("ETL run {} {} in {} ms", runId, succeeded ? "SUCCEEDED" : "FAILED", duration);
        return new EtlRunSummary(runId, succeeded, duration, results);
    }

    private SourceResult runSource(long runId, DataSource source) {
        long start = System.currentTimeMillis();
        String location = config.sources().get(source);
        if (location == null) {
            return SourceResult.failure(source, "<unset>", 0, "no location configured in app.etl.sources");
        }
        try (SourceFetcher.FetchedFile file = fetcher.fetch(location)) {
            Optional<String> lastSha = repository.lastIngestedSha256(source);
            if (!config.force() && lastSha.isPresent() && lastSha.get().equals(file.sha256())) {
                return SourceResult.skipped(source, location, file.sha256(), file.bytes(), elapsed(start));
            }
            ParseResult parsed;
            try (BufferedReader reader = Files.newBufferedReader(file.path(), StandardCharsets.UTF_8)) {
                parsed = SourceParser.parse(reader, source, ISO3_CODES, Year.now(clock).getValue());
            }
            EtlRepository.WriteCounts counts = repository.writeSource(runId, parsed);
            Map<RejectionReason, Long> byReason = parsed.rejections().stream().collect(Collectors.groupingBy(
                    Rejection::reason, () -> new EnumMap<>(RejectionReason.class), Collectors.counting()));
            return new SourceResult(source, location, file.sha256(), file.bytes(), false,
                    parsed.rowsRead(), parsed.rowsSkippedAggregate(), parsed.rowsSkippedNonIso(),
                    parsed.rowsRejected(), parsed.accepted().size(), parsed.valuesRejected(),
                    counts.inserted(), counts.updated(), counts.unchanged(), counts.nameMismatches(),
                    byReason, elapsed(start), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SourceResult.failure(source, location, elapsed(start), "interrupted");
        } catch (Exception e) {
            log.error("ETL run {}: {} failed", runId, source, e);
            return SourceResult.failure(source, location, elapsed(start), e.getMessage());
        }
    }

    private static void logResult(long runId, SourceResult r) {
        if (r.failed()) {
            log.error("ETL run {} {}: FAILED after {} ms: {}", runId, r.source(), r.durationMs(), r.error());
        } else if (r.skippedUnchanged()) {
            log.info("ETL run {} {}: skipped, file unchanged (sha256 {})", runId, r.source(), r.sha256());
        } else {
            log.info("ETL run {} {}: {} rows read; skipped {} aggregate + {} non-ISO; {} rows rejected | "
                            + "values: {} accepted ({} inserted, {} updated, {} unchanged), {} rejected {} | "
                            + "{} name mismatches | {} bytes, sha256 {} | {} ms",
                    runId, r.source(), r.rowsRead(), r.rowsSkippedAggregate(), r.rowsSkippedNonIso(), r.rowsRejected(),
                    r.valuesAccepted(), r.valuesInserted(), r.valuesUpdated(), r.valuesUnchanged(), r.valuesRejected(),
                    r.rejectionsByReason(), r.nameMismatches(), r.bytes(), r.sha256(), r.durationMs());
        }
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
