package com.ryanpurakal.pariscompass.projection;

import java.util.Map;

/**
 * The only thing the app needs from an LLM: prompt in, JSON text out, constrained by a schema.
 * Keeping the vendor behind this interface lets tests use a scripted fake and lets the model be swapped.
 */
public interface ProjectionModel {

    Reply generate(String prompt, Map<String, Object> responseJsonSchema);

    /** Configured model identifier, e.g. "gemini-2.5-flash". */
    String name();

    record Reply(String text, Integer promptTokens, Integer outputTokens) {
    }
}
