package com.ryanpurakal.pariscompass.projection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * One JSON Schema file, used twice: sent to Gemini as responseJsonSchema to constrain generation,
 * and used here to validate whatever comes back. The model is never trusted to have obeyed it.
 */
@Component
public class ProjectionSchema {
    static final String LOCATION = "projection/projection-schema.json";

    private final Map<String, Object> asMap;
    private final Schema validator;

    public ProjectionSchema(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource(LOCATION).getInputStream()) {
            JsonNode node = mapper.readTree(in);
            this.asMap = mapper.convertValue(node, new TypeReference<>() { });
            this.validator = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(node.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + LOCATION, e);
        }
    }

    /** The schema as plain maps and lists, the shape the Gemini SDK serializes. */
    public Map<String, Object> asMap() {
        return asMap;
    }

    /** Human-readable schema violations; empty means valid. */
    public List<String> validate(JsonNode instance) {
        return validator.validate(instance).stream()
                .map(e -> e.getInstanceLocation() + ": " + e.getMessage())
                .toList();
    }
}
