package com.ryanpurakal.pariscompass.controller;

import com.ryanpurakal.pariscompass.model.CountryMetrics;
import com.ryanpurakal.pariscompass.model.CountryProjectionResponse;
import com.ryanpurakal.pariscompass.model.ProjectionResponse;
import com.ryanpurakal.pariscompass.service.CountryMetricsService;
import com.ryanpurakal.pariscompass.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP boundary. Translates /api requests into service calls; contains no business logic.
 * All ISO3 codes are uppercased here before passing to services.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CountryController {
    private final GeminiService geminiService;
    private final CountryMetricsService metricsService;

    @GetMapping("/country/{iso3}")
    public ResponseEntity<CountryMetrics> getCountryMetrics(@PathVariable String iso3) {
        CountryMetrics metrics = metricsService.getLatestMetrics(iso3.toUpperCase());
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metrics);
    }

    @PostMapping("/country/{iso3}/projection")
    public ResponseEntity<CountryProjectionResponse> getCountryProjection(@PathVariable String iso3) {
        CountryMetrics metrics = metricsService.getLatestMetrics(iso3.toUpperCase());
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }

        ProjectionResponse projection = geminiService.generateProjection(iso3.toUpperCase(), metrics);

        CountryProjectionResponse response = CountryProjectionResponse.builder()
                .metrics(metrics)
                .projection(projection)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/countries")
    public ResponseEntity<?> getAllCountries() {
        return ResponseEntity.ok(metricsService.getAllCountries());
    }
}

