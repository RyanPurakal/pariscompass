package com.ryanpurakal.pariscompass.service;

import com.ryanpurakal.pariscompass.etl.MetricDefinition;
import com.ryanpurakal.pariscompass.exception.CountryNotFoundException;
import com.ryanpurakal.pariscompass.model.CountryInfo;
import com.ryanpurakal.pariscompass.model.CountryMetrics;
import com.ryanpurakal.pariscompass.repository.CountryRepository;
import com.ryanpurakal.pariscompass.repository.ObservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the latest-value snapshot for a country from the observation table.
 * Throws CountryNotFoundException for unknown codes so callers never handle null.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CountryMetricsService {
    private final CountryRepository countryRepository;
    private final ObservationRepository observationRepository;

    public CountryMetrics getLatestMetrics(String iso3) {
        String name = (iso3 == null ? null : countryRepository.findById(iso3).map(c -> c.getName()).orElse(null));
        if (name == null) {
            throw new CountryNotFoundException(iso3);
        }
        Map<String, ObservationRepository.LatestValue> latest = observationRepository.findLatestByCountry(iso3)
                .stream().collect(Collectors.toMap(ObservationRepository.LatestValue::getMetricCode, Function.identity()));

        Map<String, Integer> years = new LinkedHashMap<>();
        return CountryMetrics.builder()
                .iso3(iso3)
                .name(name)
                .co2PerCapita(pick(latest, MetricDefinition.CO2_PER_CAPITA_T, "co2PerCapita", years))
                .co2TotalMt(pick(latest, MetricDefinition.CO2_TOTAL_MT, "co2TotalMt", years))
                .temperatureAnomalyC(pick(latest, MetricDefinition.TEMPERATURE_ANOMALY_C, "temperatureAnomalyC", years))
                .renewablesSharePct(pick(latest, MetricDefinition.RENEWABLES_SHARE_ELEC_PCT, "renewablesSharePct", years))
                .years(years)
                .source(CountryMetrics.SourceInfo.builder()
                        .co2(MetricDefinition.CO2_TOTAL_MT.source().displayName())
                        .temp(MetricDefinition.TEMPERATURE_ANOMALY_C.source().displayName())
                        .renewables(MetricDefinition.RENEWABLES_SHARE_ELEC_PCT.source().displayName())
                        .build())
                .build();
    }

    public List<CountryInfo> getAllCountries() {
        return countryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> new CountryInfo(c.getIso3(), c.getName()))
                .toList();
    }

    private static Double pick(Map<String, ObservationRepository.LatestValue> latest, MetricDefinition metric,
                               String field, Map<String, Integer> years) {
        ObservationRepository.LatestValue v = latest.get(metric.code());
        if (v == null) {
            return null;
        }
        years.put(field, v.getYear());
        return v.getValue();
    }
}
