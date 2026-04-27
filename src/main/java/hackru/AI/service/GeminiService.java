package hackru.AI.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import hackru.AI.config.GeminiConfig;
import hackru.AI.model.CountryMetrics;
import hackru.AI.model.ProjectionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * External API boundary. All traffic to Google Gemini flows through this class.
 * Results are cached in-process (ConcurrentHashMap, 1-hour TTL) to avoid
 * redundant API calls for the same country within a short window.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {
    private final Client client;
    private final GeminiConfig config;
    
    // Cache: iso3 -> (timestamp, projection response)
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_HOURS = 1;

    // Original method - kept for backward compatibility
    public String askGemini(String prompt) {
        try {
            GenerateContentResponse response = client.models.generateContent(
                    config.getModelName(),
                    "Based on the following data, predict how " + prompt + " climate and emissions profile will look in 5 years. Provide a concise summary with projected CO2 trends, energy transition progress, and risk to Paris Agreement goals.",
                    null);
            return response.text();
        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new RuntimeException("Failed to generate response: " + e.getMessage(), e);
        }
    }

    // New method for country projections
    public ProjectionResponse generateProjection(String iso3, CountryMetrics metrics) {
        // Check cache
        CacheEntry cached = cache.get(iso3);
        if (cached != null && !isExpired(cached)) {
            log.info("Returning cached projection for {}", iso3);
            return cached.response();
        }

        try {
            String prompt = buildPrompt(metrics);
            log.info("Calling Gemini API for country: {}", iso3);
            
            GenerateContentResponse response = client.models.generateContent(
                    config.getModelName(),
                    prompt,
                    null);

            String projectionText = response.text();
            
            ProjectionResponse projectionResponse = ProjectionResponse.builder()
                    .country(metrics.getName())
                    .projection(projectionText)
                    .model(config.getModelName())
                    .generatedAt(Instant.now())
                    .build();

            // Cache the result
            cache.put(iso3, new CacheEntry(Instant.now(), projectionResponse));
            
            return projectionResponse;
        } catch (Exception e) {
            log.error("Error calling Gemini API for country: {}", iso3, e);
            throw new RuntimeException("Failed to generate projection: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(CountryMetrics metrics) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Based on the provided metrics for ").append(metrics.getName()).append(" (").append(metrics.getIso3()).append("), ");
        prompt.append("predict how the country's climate and emissions profile will look in 5 years.\n\n");
        
        prompt.append("Current metrics (year ").append(metrics.getYear()).append("):\n");
        if (metrics.getCo2PerCapita() != null) {
            prompt.append("- CO2 per capita: ").append(metrics.getCo2PerCapita()).append(" tons\n");
        }
        if (metrics.getCo2TotalMt() != null) {
            prompt.append("- Total CO2 emissions: ").append(metrics.getCo2TotalMt()).append(" million tons\n");
        }
        if (metrics.getTemperatureAnomalyC() != null) {
            prompt.append("- Temperature anomaly: ").append(metrics.getTemperatureAnomalyC()).append("°C\n");
        }
        if (metrics.getRenewablesSharePct() != null) {
            prompt.append("- Renewable energy share: ").append(metrics.getRenewablesSharePct()).append("%\n");
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

    private boolean isExpired(CacheEntry entry) {
        long hoursSinceCreation = Instant.now().getEpochSecond() - entry.timestamp().getEpochSecond();
        return TimeUnit.SECONDS.toHours(hoursSinceCreation) >= CACHE_TTL_HOURS;
    }

    private record CacheEntry(Instant timestamp, ProjectionResponse response) {}
}
