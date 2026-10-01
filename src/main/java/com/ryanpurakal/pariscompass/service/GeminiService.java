package com.ryanpurakal.pariscompass.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.ryanpurakal.pariscompass.config.AppProperties;
import com.ryanpurakal.pariscompass.exception.ProjectionUnavailableException;
import com.ryanpurakal.pariscompass.model.CountryMetrics;
import com.ryanpurakal.pariscompass.model.ProjectionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * External API boundary. All traffic to Google Gemini flows through this class.
 * Results are cached in-process (ConcurrentHashMap, 1-hour TTL) to avoid
 * redundant API calls for the same country within a short window.
 * The Client bean is optional: without an API key, projections return 503.
 */
@Slf4j
@Service
public class GeminiService {
    private final Client client;
    private final String modelName;

    public GeminiService(ObjectProvider<Client> clientProvider, AppProperties properties) {
        this.client = clientProvider.getIfAvailable();
        this.modelName = properties.gemini().model();
        if (client == null) {
            log.warn("GEMINI_API_KEY not set: projection endpoint will return 503");
        }
    }

    // Cache: iso3 -> (timestamp, projection response)
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_HOURS = 1;

    public ProjectionResponse generateProjection(String iso3, CountryMetrics metrics) {
        // Check cache
        CacheEntry cached = cache.get(iso3);
        if (cached != null && !isExpired(cached)) {
            log.info("Returning cached projection for {}", iso3);
            return cached.response();
        }
        if (client == null) {
            throw new ProjectionUnavailableException("AI projections are not configured on this server.");
        }

        try {
            String prompt = buildPrompt(metrics);
            log.info("Calling Gemini API for country: {}", iso3);
            
            GenerateContentResponse response = client.models.generateContent(
                    modelName,
                    prompt,
                    null);

            String projectionText = response.text();
            
            ProjectionResponse projectionResponse = ProjectionResponse.builder()
                    .country(metrics.getName())
                    .projection(projectionText)
                    .model(modelName)
                    .generatedAt(Instant.now())
                    .build();

            // Cache the result
            cache.put(iso3, new CacheEntry(Instant.now(), projectionResponse));
            
            return projectionResponse;
        } catch (Exception e) {
            throw new ProjectionUnavailableException(
                    "The projection service is temporarily unavailable. Try again later.", e);
        }
    }

    private String buildPrompt(CountryMetrics metrics) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Based on the provided metrics for ").append(metrics.getName()).append(" (").append(metrics.getIso3()).append("), ");
        prompt.append("predict how the country's climate and emissions profile will look in 5 years.\n\n");
        
        prompt.append("Latest available metrics:\n");
        if (metrics.getCo2PerCapita() != null) {
            prompt.append("- CO2 per capita: ").append(metrics.getCo2PerCapita()).append(" tons").append(yearOf(metrics, "co2PerCapita")).append("\n");
        }
        if (metrics.getCo2TotalMt() != null) {
            prompt.append("- Total CO2 emissions: ").append(metrics.getCo2TotalMt()).append(" million tons").append(yearOf(metrics, "co2TotalMt")).append("\n");
        }
        if (metrics.getTemperatureAnomalyC() != null) {
            prompt.append("- Temperature anomaly: ").append(metrics.getTemperatureAnomalyC()).append("°C vs 1991-2020 mean").append(yearOf(metrics, "temperatureAnomalyC")).append("\n");
        }
        if (metrics.getRenewablesSharePct() != null) {
            prompt.append("- Renewables share of electricity: ").append(metrics.getRenewablesSharePct()).append("%").append(yearOf(metrics, "renewablesSharePct")).append("\n");
        }
        
        prompt.append("\nRequirements:\n");
        prompt.append("1. Provide a concise summary in 6-10 sentences.\n");
        prompt.append("2. Include projected CO2 trend direction (increasing/decreasing/stable).\n");
        prompt.append("3. Identify key drivers: energy mix evolution, policy changes, industrial growth.\n");
        prompt.append("4. Assess Paris Agreement alignment risk level: Low, Medium, or High.\n");
        prompt.append("5. Only reference the provided metrics - do not invent sources or data.\n");
        prompt.append("6. Be specific about the projected trajectory based on current trends.\n");
        
        return prompt.toString();
    }

    private static String yearOf(CountryMetrics metrics, String field) {
        Integer year = metrics.getYears() == null ? null : metrics.getYears().get(field);
        return year == null ? "" : " (" + year + ")";
    }

    private boolean isExpired(CacheEntry entry) {
        long hoursSinceCreation = Instant.now().getEpochSecond() - entry.timestamp().getEpochSecond();
        return TimeUnit.SECONDS.toHours(hoursSinceCreation) >= CACHE_TTL_HOURS;
    }

    private record CacheEntry(Instant timestamp, ProjectionResponse response) {}
}
