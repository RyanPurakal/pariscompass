package com.ryanpurakal.pariscompass.etl;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses and validates one source CSV. Pure: no Spring, no database, so every rule is unit tested.
 *
 * Row classification, in order:
 * <ol>
 *   <li>blank code: a regional aggregate such as "Africa (GCP)"; skipped, not rejected</li>
 *   <li>code starting with OWID_: OWID's own marker for non-ISO entities (Kosovo, World); skipped</li>
 *   <li>code not in ISO 3166-1 alpha-3: rejected (UNKNOWN_ISO3)</li>
 *   <li>bad year, year out of range, repeated (country, year): rejected</li>
 * </ol>
 * Each metric value in an accepted row is then checked on its own: blank means "no data" and is
 * ignored, a non-finite number or a value outside the metric's bounds is rejected.
 * Skips and rejections are counted separately so aggregates do not inflate the rejection rate.
 */
public final class SourceParser {
    static final int MIN_YEAR = 1750;
    private static final String NON_ISO_PREFIX = "OWID_";
    private static final int MAX_RAW_LENGTH = 64;

    private SourceParser() {
    }

    public static ParseResult parse(Reader input, DataSource source, Set<String> isoCodes, int maxYear)
            throws IOException {
        List<MetricDefinition> metrics = MetricDefinition.forSource(source);
        try (CSVReader csv = new CSVReader(input)) {
            String[] header = csv.readNext();
            if (header == null) {
                throw new IllegalStateException(source + ": file is empty");
            }
            Map<String, Integer> columns = indexColumns(header);
            int isoIdx = require(columns, source.isoColumn(), source);
            int nameIdx = require(columns, source.nameColumn(), source);
            int yearIdx = require(columns, source.yearColumn(), source);
            int[] metricIdx = metrics.stream().mapToInt(m -> require(columns, m.sourceColumn(), source)).toArray();

            int rowsRead = 0;
            int skippedAggregate = 0;
            int skippedNonIso = 0;
            List<ParsedObservation> accepted = new ArrayList<>();
            List<Rejection> rejections = new ArrayList<>();
            Map<String, String> names = new LinkedHashMap<>();
            Set<String> seen = new HashSet<>();

            String[] row;
            while ((row = csv.readNext()) != null) {
                rowsRead++;
                int line = (int) csv.getLinesRead();
                String iso3 = cell(row, isoIdx);
                String yearRaw = cell(row, yearIdx);

                if (iso3.isEmpty()) {
                    skippedAggregate++;
                    continue;
                }
                if (iso3.startsWith(NON_ISO_PREFIX)) {
                    skippedNonIso++;
                    continue;
                }
                if (!isoCodes.contains(iso3)) {
                    rejections.add(rowRejection(line, iso3, yearRaw, RejectionReason.UNKNOWN_ISO3,
                            "'" + iso3 + "' (" + cell(row, nameIdx) + ") is not an ISO 3166-1 alpha-3 code"));
                    continue;
                }
                int year;
                try {
                    year = Integer.parseInt(yearRaw);
                } catch (NumberFormatException e) {
                    rejections.add(rowRejection(line, iso3, yearRaw, RejectionReason.INVALID_YEAR,
                            "year is not an integer"));
                    continue;
                }
                if (year < MIN_YEAR || year > maxYear) {
                    rejections.add(rowRejection(line, iso3, yearRaw, RejectionReason.YEAR_OUT_OF_RANGE,
                            "year outside [" + MIN_YEAR + ", " + maxYear + "]"));
                    continue;
                }
                if (!seen.add(iso3 + ":" + year)) {
                    rejections.add(rowRejection(line, iso3, yearRaw, RejectionReason.DUPLICATE_ROW,
                            "(" + iso3 + ", " + year + ") already appeared earlier in the file"));
                    continue;
                }
                int acceptedBefore = accepted.size();
                for (int i = 0; i < metrics.size(); i++) {
                    MetricDefinition metric = metrics.get(i);
                    String raw = cell(row, metricIdx[i]);
                    if (raw.isEmpty()) {
                        continue;
                    }
                    double value;
                    try {
                        value = Double.parseDouble(raw);
                    } catch (NumberFormatException e) {
                        value = Double.NaN;
                    }
                    if (!Double.isFinite(value)) {
                        rejections.add(new Rejection(line, iso3, yearRaw, metric.code(), truncate(raw),
                                RejectionReason.NON_NUMERIC, "not a finite number"));
                    } else if (!metric.inRange(value)) {
                        rejections.add(new Rejection(line, iso3, yearRaw, metric.code(), truncate(raw),
                                RejectionReason.OUT_OF_RANGE,
                                value + " outside [" + fmt(metric.min()) + ", " + fmt(metric.max()) + "] " + metric.unit()));
                    } else {
                        accepted.add(new ParsedObservation(iso3, metric.code(), year, value));
                    }
                }
                // Only countries with at least one accepted value get a name, so no empty country rows.
                if (accepted.size() > acceptedBefore) {
                    names.putIfAbsent(iso3, cell(row, nameIdx));
                }
            }
            return new ParseResult(source, rowsRead, skippedAggregate, skippedNonIso, accepted, rejections, names);
        } catch (CsvValidationException e) {
            throw new IOException(source + ": malformed CSV: " + e.getMessage(), e);
        }
    }

    private static Map<String, Integer> indexColumns(String[] header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            // Strip a UTF-8 BOM from the first header cell if present.
            columns.put(header[i].replace("﻿", "").trim(), i);
        }
        return columns;
    }

    /** A missing column means the upstream schema changed; fail the source loudly rather than load nothing. */
    private static int require(Map<String, Integer> columns, String name, DataSource source) {
        Integer idx = columns.get(name);
        if (idx == null) {
            throw new IllegalStateException(source + ": expected column '" + name + "' not found in header");
        }
        return idx;
    }

    private static String cell(String[] row, int idx) {
        return idx < row.length ? row[idx].trim() : "";
    }

    private static Rejection rowRejection(int line, String iso3, String year, RejectionReason reason, String detail) {
        return new Rejection(line, truncate(iso3), truncate(year), null, null, reason, detail);
    }

    private static String truncate(String s) {
        return s.length() <= MAX_RAW_LENGTH ? s : s.substring(0, MAX_RAW_LENGTH);
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
