package com.ryanpurakal.pariscompass.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.ryanpurakal.pariscompass.config.GeminiConfig;
import com.ryanpurakal.pariscompass.support.FakeHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The real Gemini adapter and SDK, talking HTTP to a local fake of the Gemini REST API.
 * Verifies what goes on the wire (model, JSON mode, schema, temperature) and how the reply is read.
 */
class GeminiProjectionModelTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeHttpServer server;

    @BeforeEach
    void start() throws Exception {
        server = new FakeHttpServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    /** The production client settings from GeminiConfig, pointed at the local server. */
    private GeminiProjectionModel model() {
        HttpOptions options = GeminiConfig.httpOptions().toBuilder().baseUrl(server.url()).build();
        Client client = Client.builder().apiKey("test-key").httpOptions(options).build();
        return new GeminiProjectionModel(client, "gemini-2.5-flash");
    }

    private static String geminiReply(String text) throws Exception {
        return MAPPER.writeValueAsString(Map.of(
                "candidates", new Object[]{Map.of(
                        "content", Map.of("role", "model", "parts", new Object[]{Map.of("text", text)}),
                        "finishReason", "STOP")},
                "usageMetadata", Map.of("promptTokenCount", 1752, "candidatesTokenCount", 450, "totalTokenCount", 2202),
                "modelVersion", "gemini-2.5-flash"));
    }

    @Test
    void sendsJsonModeWithSchemaAndReadsTextAndTokens() throws Exception {
        server.respond(200, geminiReply("{\"summary\":\"ok\"}"), "application/json");
        Map<String, Object> schema = new ProjectionSchema(MAPPER).asMap();

        ProjectionModel.Reply reply = model().generate("Project France's CO2.", schema);

        assertThat(reply.text()).isEqualTo("{\"summary\":\"ok\"}");
        assertThat(reply.promptTokens()).isEqualTo(1752);
        assertThat(reply.outputTokens()).isEqualTo(450);

        FakeHttpServer.Request request = server.requests().get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).endsWith("/models/gemini-2.5-flash:generateContent");
        assertThat(request.apiKeyHeader()).isEqualTo("test-key");
        JsonNode body = MAPPER.readTree(request.body());
        assertThat(body.at("/contents/0/parts/0/text").asText()).isEqualTo("Project France's CO2.");
        JsonNode config = body.get("generationConfig");
        assertThat(config.get("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(config.get("temperature").floatValue()).isEqualTo(0.2f);
        assertThat(config.at("/responseJsonSchema/title").asText()).isEqualTo("CountryProjection");
        assertThat(config.at("/responseJsonSchema/required")).hasSize(6);
    }

    @Test
    void serverErrorIsNotRetriedBySdkAndSurfacesForTheServiceToHandle() {
        server.respond(503, "{\"error\":{\"code\":503,\"message\":\"overloaded\",\"status\":\"UNAVAILABLE\"}}", "application/json");

        assertThatThrownBy(() -> model().generate("p", Map.of())).isInstanceOf(RuntimeException.class);
        // The SDK default made 3 attempts here; GeminiConfig limits it to 1.
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void exposesConfiguredModelName() {
        assertThat(model().name()).isEqualTo("gemini-2.5-flash");
    }
}
