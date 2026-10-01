package com.ryanpurakal.pariscompass.service;

import com.ryanpurakal.pariscompass.domain.Country;
import com.ryanpurakal.pariscompass.etl.MetricDefinition;
import com.ryanpurakal.pariscompass.exception.CountryNotFoundException;
import com.ryanpurakal.pariscompass.exception.InvalidRequestException;
import com.ryanpurakal.pariscompass.model.ComparisonResponse;
import com.ryanpurakal.pariscompass.model.CountrySeriesResponse;
import com.ryanpurakal.pariscompass.model.MetricInfo;
import com.ryanpurakal.pariscompass.model.MetricSeries;
import com.ryanpurakal.pariscompass.model.RankingResponse;
import com.ryanpurakal.pariscompass.model.SeriesPoint;
import com.ryanpurakal.pariscompass.repository.AnalyticsRepository;
import com.ryanpurakal.pariscompass.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Time series, comparisons, rankings and the metric catalog. Validation of request semantics lives here. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {
    static final int MIN_COMPARE = 2;
    static final int MAX_COMPARE = 4;
    private static final int MIN_YEAR = 1750;
    private static final int MAX_YEAR = 2100;

    private final AnalyticsRepository analytics;
    private final CountryRepository countries;

    public List<MetricInfo> metricCatalog() {
        return analytics.findMetricCatalog().stream().map(r -> new MetricInfo(
                r.getCode(), r.getName(), r.getUnit(), r.getDescription(),
                new MetricInfo.Source(r.getSourceCode(), r.getSourceName(), r.getSourceHomepage(), r.getSourceCitation()),
                new MetricInfo.Coverage(r.getValueCount(), r.getCountries(), r.getFirstYear(), r.getLastYear(),
                        r.getValueCount() == 0 ? null : analytics.findDefaultYear(r.getCode()))))
                .toList();
    }

    /** @param metricCodes null or empty means every metric */
    public CountrySeriesResponse countrySeries(String iso3, List<String> metricCodes, Integer from, Integer to) {
        Country country = requireCountry(iso3);
        checkYearRange(from, to);
        List<MetricDefinition> metrics = metricCodes == null || metricCodes.isEmpty()
                ? Arrays.asList(MetricDefinition.values())
                : metricCodes.stream().map(AnalyticsService::requireMetric).distinct().toList();

        Map<String, List<SeriesPoint>> byMetric = analytics.findSeries(country.getIso3(),
                        metrics.stream().map(MetricDefinition::code).toList(), lower(from), upper(to)).stream()
                .collect(Collectors.groupingBy(AnalyticsRepository.MetricPoint::getMetricCode, LinkedHashMap::new,
                        Collectors.mapping(p -> new SeriesPoint(p.getYear(), p.getValue()), Collectors.toList())));

        List<MetricSeries> series = metrics.stream()
                .map(m -> new MetricSeries(m.code(), m.unit(), byMetric.getOrDefault(m.code(), List.of())))
                .toList();
        return new CountrySeriesResponse(country.getIso3(), country.getName(), from, to, series);
    }

    public ComparisonResponse compare(List<String> iso3Codes, String metricCode, Integer from, Integer to) {
        MetricDefinition metric = requireMetric(metricCode);
        checkYearRange(from, to);
        List<String> codes = iso3Codes == null ? List.of() : new ArrayList<>(iso3Codes.stream()
                .map(c -> c.trim().toUpperCase(Locale.ROOT)).filter(c -> !c.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        if (codes.size() < MIN_COMPARE || codes.size() > MAX_COMPARE) {
            throw new InvalidRequestException("INVALID_COUNTRY_LIST",
                    "countries must list " + MIN_COMPARE + " to " + MAX_COMPARE + " distinct ISO3 codes, got " + codes.size());
        }
        Map<String, Country> found = countries.findAllById(codes).stream()
                .collect(Collectors.toMap(Country::getIso3, Function.identity()));
        codes.stream().filter(c -> !found.containsKey(c)).findFirst().ifPresent(missing -> {
            throw new CountryNotFoundException(missing);
        });

        Map<String, List<SeriesPoint>> byCountry = analytics.findComparison(codes, metric.code(), lower(from), upper(to))
                .stream().collect(Collectors.groupingBy(AnalyticsRepository.CountryPoint::getIso3,
                        Collectors.mapping(p -> new SeriesPoint(p.getYear(), p.getValue()), Collectors.toList())));

        List<ComparisonResponse.CountrySeries> series = codes.stream()
                .map(c -> new ComparisonResponse.CountrySeries(c, found.get(c).getName(), byCountry.getOrDefault(c, List.of())))
                .toList();
        return new ComparisonResponse(metric.code(), metric.unit(), from, to, series);
    }

    public RankingResponse rankings(String metricCode, Integer year, String order, int limit) {
        MetricDefinition metric = requireMetric(metricCode);
        boolean ascending = switch (order == null ? "desc" : order.toLowerCase(Locale.ROOT)) {
            case "desc" -> false;
            case "asc" -> true;
            default -> throw new InvalidRequestException("INVALID_ORDER", "order must be 'asc' or 'desc'");
        };
        Integer resolvedYear = year != null ? year : analytics.findDefaultYear(metric.code());
        if (resolvedYear == null) {
            return new RankingResponse(metric.code(), metric.unit(), 0, ascending ? "asc" : "desc", 0, List.of());
        }
        List<AnalyticsRepository.RankRow> rows = ascending
                ? analytics.findRankingAsc(metric.code(), resolvedYear, limit)
                : analytics.findRankingDesc(metric.code(), resolvedYear, limit);
        int total = rows.isEmpty() ? 0 : rows.get(0).getTotal();
        return new RankingResponse(metric.code(), metric.unit(), resolvedYear, ascending ? "asc" : "desc", total,
                rows.stream().map(r -> new RankingResponse.Entry(r.getRank(), r.getIso3(), r.getName(), r.getValue())).toList());
    }

    private Country requireCountry(String iso3) {
        return countries.findById(iso3).orElseThrow(() -> new CountryNotFoundException(iso3));
    }

    static MetricDefinition requireMetric(String code) {
        return MetricDefinition.byCode(code == null ? "" : code.trim()).orElseThrow(() -> new InvalidRequestException(
                "UNKNOWN_METRIC", "Unknown metric '" + code + "'. See GET /api/metrics for valid codes."));
    }

    private static void checkYearRange(Integer from, Integer to) {
        if (from != null && to != null && from > to) {
            throw new InvalidRequestException("INVALID_YEAR_RANGE", "from (" + from + ") must not be after to (" + to + ")");
        }
    }

    private static int lower(Integer from) {
        return from == null ? MIN_YEAR : from;
    }

    private static int upper(Integer to) {
        return to == null ? MAX_YEAR : to;
    }
}
