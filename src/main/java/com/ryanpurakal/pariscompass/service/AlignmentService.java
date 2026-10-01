package com.ryanpurakal.pariscompass.service;

import com.ryanpurakal.pariscompass.domain.Country;
import com.ryanpurakal.pariscompass.etl.MetricDefinition;
import com.ryanpurakal.pariscompass.exception.CountryNotFoundException;
import com.ryanpurakal.pariscompass.model.AlignmentResponse;
import com.ryanpurakal.pariscompass.model.SeriesPoint;
import com.ryanpurakal.pariscompass.repository.AnalyticsRepository;
import com.ryanpurakal.pariscompass.repository.CountryRepository;
import com.ryanpurakal.pariscompass.scoring.AlignmentScorer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Loads a country's history and hands it to the pure AlignmentScorer. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlignmentService {
    static final String DISCLAIMER = "Indicator built from historical emissions and electricity data only. "
            + "It does not assess national pledges (NDCs) or policies and is not an official Paris Agreement rating. "
            + "See SCORING.md.";

    private final CountryRepository countries;
    private final AnalyticsRepository analytics;

    public AlignmentResponse score(String iso3) {
        Country country = countries.findById(iso3).orElseThrow(() -> new CountryNotFoundException(iso3));
        List<String> metrics = List.of(MetricDefinition.CO2_TOTAL_MT.code(), MetricDefinition.CO2_PER_CAPITA_T.code(),
                MetricDefinition.LOW_CARBON_SHARE_ELEC_PCT.code());
        Map<String, List<SeriesPoint>> series = analytics.findSeries(iso3, metrics, 1750, 2100).stream()
                .collect(Collectors.groupingBy(AnalyticsRepository.MetricPoint::getMetricCode,
                        Collectors.mapping(p -> new SeriesPoint(p.getYear(), p.getValue()), Collectors.toList())));

        AlignmentScorer.AlignmentResult r = AlignmentScorer.score(
                series.getOrDefault(MetricDefinition.CO2_TOTAL_MT.code(), List.of()),
                series.getOrDefault(MetricDefinition.CO2_PER_CAPITA_T.code(), List.of()),
                series.getOrDefault(MetricDefinition.LOW_CARBON_SHARE_ELEC_PCT.code(), List.of()));
        return new AlignmentResponse(country.getIso3(), country.getName(), r.formulaVersion(), r.score(), r.band(),
                r.components(), r.reason(), DISCLAIMER);
    }
}
