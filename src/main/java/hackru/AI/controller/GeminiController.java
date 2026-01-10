package hackru.AI.controller;

import hackru.AI.model.CountryMetrics;
import hackru.AI.model.CountryProjectionResponse;
import hackru.AI.model.ProjectionRequest;
import hackru.AI.model.ProjectionResponse;
import hackru.AI.service.CountryMetricsService;
import hackru.AI.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GeminiController {
    private final GeminiService geminiService;
    private final CountryMetricsService metricsService;

    // Original endpoint - kept for backward compatibility (now using POST)
    @PostMapping("/gemini/ask")
    public String askGeminiAPI(@RequestBody String prompt) {
        return geminiService.askGemini(prompt);
    }

    @PostMapping("/projection")
    public ResponseEntity<ProjectionResponse> getProjection(@RequestBody ProjectionRequest request) {
        if (request.getCountry() == null || request.getCountry().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        String iso3 = metricsService.findIso3ByName(request.getCountry());
        if (iso3 == null) {
            return ResponseEntity.notFound().build();
        }

        CountryMetrics metrics = metricsService.getLatestMetrics(iso3);
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }

        ProjectionResponse projection = geminiService.generateProjection(iso3, metrics);
        return ResponseEntity.ok(projection);
    }

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

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "Paris Compass"));
    }
}

