package com.ryanpurakal.pariscompass.projection;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;

import java.util.Map;
import java.util.Optional;

/**
 * Gemini adapter. Asks for application/json constrained by the schema, at low temperature so repeated
 * calls on the same data stay close. The caller still validates the reply; this class does not trust it.
 */
public class GeminiProjectionModel implements ProjectionModel {
    static final float TEMPERATURE = 0.2f;

    private final Client client;
    private final String model;

    public GeminiProjectionModel(Client client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public Reply generate(String prompt, Map<String, Object> responseJsonSchema) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseJsonSchema(responseJsonSchema)
                .temperature(TEMPERATURE)
                .build();
        GenerateContentResponse response = client.models.generateContent(model, prompt, config);
        Optional<GenerateContentResponseUsageMetadata> usage = response.usageMetadata();
        return new Reply(response.text(),
                usage.flatMap(GenerateContentResponseUsageMetadata::promptTokenCount).orElse(null),
                usage.flatMap(GenerateContentResponseUsageMetadata::candidatesTokenCount).orElse(null));
    }

    @Override
    public String name() {
        return model;
    }
}
