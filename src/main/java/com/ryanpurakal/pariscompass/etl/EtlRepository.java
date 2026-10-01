package com.ryanpurakal.pariscompass.etl;

import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * All ETL writes. Plain JDBC rather than JPA: the load is ~200k rows of bulk set-based work,
 * where per-entity persistence would add overhead and give no benefit. JPA is used for reads.
 */
@Slf4j
@Repository
public class EtlRepository {
    /** Detailed rejection rows kept per source per run. Counts are always exact; this caps storage. */
    static final int MAX_STORED_REJECTIONS = 10_000;
    /** Arbitrary constant identifying the ETL's advisory lock. */
    private static final long ETL_LOCK_KEY = 0x5041524953L;

    private final JdbcTemplate jdbc;

    public EtlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Upserts data_source and metric rows from the enums, so code stays the single source of truth. */
    @Transactional
    public void syncCatalog() {
        for (DataSource s : DataSource.values()) {
            jdbc.update("""
                    INSERT INTO data_source (code, name, homepage, citation) VALUES (?, ?, ?, ?)
                    ON CONFLICT (code) DO UPDATE
                      SET name = EXCLUDED.name, homepage = EXCLUDED.homepage, citation = EXCLUDED.citation
                    """, s.name(), s.displayName(), s.homepage(), s.citation());
        }
        for (MetricDefinition m : MetricDefinition.values()) {
            jdbc.update("""
                    INSERT INTO metric (code, name, unit, source_code, description) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (code) DO UPDATE
                      SET name = EXCLUDED.name, unit = EXCLUDED.unit,
                          source_code = EXCLUDED.source_code, description = EXCLUDED.description
                    """, m.code(), m.displayName(), m.unit(), m.source().name(), m.description());
        }
    }

    public long startRun(boolean forced) {
        return jdbc.queryForObject(
                "INSERT INTO etl_run (started_at, status, forced) VALUES (?, 'RUNNING', ?) RETURNING id",
                Long.class, Timestamp.from(Instant.now()), forced);
    }

    public void finishRun(long runId, boolean succeeded, String error) {
        jdbc.update("UPDATE etl_run SET finished_at = ?, status = ?, error = ? WHERE id = ?",
                Timestamp.from(Instant.now()), succeeded ? "SUCCEEDED" : "FAILED", error, runId);
    }

    /** Hash of the file last ingested successfully for this source (skipped runs do not count). */
    public Optional<String> lastIngestedSha256(DataSource source) {
        return jdbc.query("""
                SELECT r.sha256 FROM etl_source_result r
                WHERE r.source_code = ? AND r.error IS NULL AND NOT r.skipped_unchanged
                ORDER BY r.run_id DESC LIMIT 1
                """, (rs, i) -> rs.getString(1), source.name()).stream().findFirst();
    }

    /**
     * Writes one source atomically: countries, observations and rejections commit together or not at all.
     * Observations go through a COPY-loaded temp table, then one set-based upsert.
     */
    @Transactional
    public WriteCounts writeSource(long runId, ParseResult parsed) {
        // Serialize concurrent ETL runs; released automatically at commit or rollback.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, ETL_LOCK_KEY);

        int nameMismatches = upsertCountries(parsed);

        jdbc.execute("""
                CREATE TEMP TABLE etl_staging (
                    iso3 CHAR(3), metric_code VARCHAR(64), year SMALLINT, value DOUBLE PRECISION
                ) ON COMMIT DROP
                """);
        copyIntoStaging(parsed.accepted());

        // Classify before upserting: new keys are inserts, existing keys with a different value are updates.
        Map<String, Object> pre = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE o.iso3 IS NULL) AS inserted,
                       count(*) FILTER (WHERE o.iso3 IS NOT NULL AND o.value IS DISTINCT FROM s.value) AS updated
                FROM etl_staging s
                LEFT JOIN observation o USING (iso3, metric_code, year)
                """);
        int inserted = ((Number) pre.get("inserted")).intValue();
        int updated = ((Number) pre.get("updated")).intValue();

        int affected = jdbc.update("""
                INSERT INTO observation (iso3, metric_code, year, value, etl_run_id)
                SELECT iso3, metric_code, year, value, ? FROM etl_staging
                ON CONFLICT (iso3, metric_code, year) DO UPDATE
                  SET value = EXCLUDED.value, etl_run_id = EXCLUDED.etl_run_id, updated_at = now()
                  WHERE observation.value IS DISTINCT FROM EXCLUDED.value
                """, runId);
        if (affected != inserted + updated) {
            // Cannot happen while the advisory lock is held; checked so a regression is visible.
            throw new IllegalStateException("Upsert touched " + affected + " rows, expected " + (inserted + updated));
        }

        insertRejections(runId, parsed);
        int unchanged = parsed.accepted().size() - inserted - updated;
        return new WriteCounts(inserted, updated, unchanged, nameMismatches);
    }

    public void recordSourceResult(long runId, SourceResult r) {
        jdbc.update("""
                INSERT INTO etl_source_result (run_id, source_code, location, sha256, bytes, skipped_unchanged,
                    rows_read, rows_skipped_aggregate, rows_skipped_non_iso, rows_rejected,
                    values_accepted, values_rejected, values_inserted, values_updated, values_unchanged,
                    name_mismatches, duration_ms, error)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, runId, r.source().name(), r.location(), r.sha256(), r.bytes(), r.skippedUnchanged(),
                r.rowsRead(), r.rowsSkippedAggregate(), r.rowsSkippedNonIso(), r.rowsRejected(),
                r.valuesAccepted(), r.valuesRejected(), r.valuesInserted(), r.valuesUpdated(), r.valuesUnchanged(),
                r.nameMismatches(), r.durationMs(), r.error());
    }

    /** Inserts unseen countries (first source to mention a country names it) and counts name disagreements. */
    private int upsertCountries(ParseResult parsed) {
        Map<String, String> existing = new HashMap<>();
        jdbc.query("SELECT iso3, name FROM country", rs -> {
            existing.put(rs.getString(1), rs.getString(2));
        });
        List<Object[]> toInsert = new ArrayList<>();
        int mismatches = 0;
        for (Map.Entry<String, String> e : parsed.countryNames().entrySet()) {
            String known = existing.get(e.getKey());
            if (known == null) {
                toInsert.add(new Object[]{e.getKey(), e.getValue()});
            } else if (!known.equals(e.getValue())) {
                mismatches++;
                log.debug("{}: {} is '{}' here but '{}' in the country table",
                        parsed.source(), e.getKey(), e.getValue(), known);
            }
        }
        jdbc.batchUpdate("INSERT INTO country (iso3, name) VALUES (?, ?) ON CONFLICT (iso3) DO NOTHING", toInsert);
        return mismatches;
    }

    private void copyIntoStaging(List<ParsedObservation> rows) {
        StringBuilder csv = new StringBuilder(rows.size() * 40);
        for (ParsedObservation o : rows) {
            csv.append(o.iso3()).append(',').append(o.metricCode()).append(',')
                    .append(o.year()).append(',').append(o.value()).append('\n');
        }
        jdbc.execute((ConnectionCallback<Long>) con -> {
            try {
                return con.unwrap(PGConnection.class).getCopyAPI()
                        .copyIn("COPY etl_staging (iso3, metric_code, year, value) FROM STDIN (FORMAT csv)",
                                new StringReader(csv.toString()));
            } catch (java.io.IOException e) {
                throw new java.sql.SQLException("COPY into staging failed", e);
            }
        });
    }

    private void insertRejections(long runId, ParseResult parsed) {
        List<Rejection> rejections = parsed.rejections();
        if (rejections.size() > MAX_STORED_REJECTIONS) {
            log.warn("{}: {} rejections, storing the first {}", parsed.source(), rejections.size(), MAX_STORED_REJECTIONS);
            rejections = rejections.subList(0, MAX_STORED_REJECTIONS);
        }
        jdbc.batchUpdate("""
                INSERT INTO etl_rejection (run_id, source_code, line_number, iso3, year, metric_code, raw_value, reason, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rejections.stream().map(r -> new Object[]{runId, parsed.source().name(), r.lineNumber(),
                r.iso3(), r.year(), r.metricCode(), r.rawValue(), r.reason().name(), r.detail()}).toList());
    }

    public record WriteCounts(int inserted, int updated, int unchanged, int nameMismatches) {
    }
}
