package com.rajan.resumetailor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rajan.resumetailor.config.GeminiProperties;
import com.rajan.resumetailor.dto.CoachAction;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GeminiResumeCoachClient {

    private static final String SYSTEM_INSTRUCTIONS = """
            You are a resume coach focused on truthful, job-aligned resume improvements.
            Rules:
            - Do not invent experience, employers, titles, tools, metrics, certifications, or years of experience.
            - Only propose edits that are supported by the provided bullet text.
            - Preserve any numbers/metrics/tools/entities already present; do not add new numeric claims.
            - Prefer natural, human phrasing; avoid keyword stuffing.
            - If critical information is missing, ask a clarifying question instead of guessing.
            - Return JSON that exactly matches the provided schema.
            """;

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final GeminiApiSupport geminiApiSupport;
    private final AiCallExecutor aiCallExecutor;
    private final GeminiApiKeyResolver geminiApiKeyResolver;

    public GeminiResumeCoachClient(
            GeminiProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            GeminiApiSupport geminiApiSupport,
            AiCallExecutor aiCallExecutor,
            GeminiApiKeyResolver geminiApiKeyResolver
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.geminiApiSupport = geminiApiSupport;
        this.aiCallExecutor = aiCallExecutor;
        this.geminiApiKeyResolver = geminiApiKeyResolver;
    }

    public CoachChatPayload chat(String prompt) {
        String apiKey = geminiApiKeyResolver.resolveForCurrentRequest();

        try {
            HttpRequest request = HttpRequest.newBuilder(buildGenerateContentUri())
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(properties.getTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(prompt), StandardCharsets.UTF_8))
                    .build();

            return aiCallExecutor.execute("gemini.coachChat", () -> {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 400) {
                    throw geminiApiSupport.buildGeminiException(objectMapper, response.body(), response.statusCode());
                }
                return parsePayloadFromGeminiResponse(response.body());
            });
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request failed: " + exception.getMessage(), exception);
        }
    }

    private URI buildGenerateContentUri() {
        return geminiApiSupport.buildGenerateContentUri(properties.getApiBaseUrl(), properties.getModel());
    }

    String buildRequestBody(String prompt) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode systemInstruction = root.putObject("system_instruction");
        systemInstruction.putArray("parts")
                .addObject()
                .put("text", SYSTEM_INSTRUCTIONS);

        root.putArray("contents")
                .addObject()
                .putArray("parts")
                .addObject()
                .put("text", prompt);

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseJsonSchema", buildSchema());

        return objectMapper.writeValueAsString(root);
    }

    private JsonNode buildSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");

        ObjectNode propertiesNode = root.putObject("properties");
        propertiesNode.set("assistantMessage", objectMapper.createObjectNode()
                .put("type", "string")
                .put("description", "A helpful, concise coach reply addressed to the user."));

        ObjectNode actionItem = objectMapper.createObjectNode();
        actionItem.put("type", "object");
        ObjectNode actionProps = actionItem.putObject("properties");
        actionProps.set("type", objectMapper.createObjectNode()
                .put("type", "string")
                .put("description", "One of: edit_bullet, pin_keywords, ask_user."));
        actionProps.set("bulletIndex", objectMapper.createObjectNode().put("type", "integer"));
        actionProps.set("suggestedText", objectMapper.createObjectNode().put("type", "string"));
        actionProps.set("reason", objectMapper.createObjectNode().put("type", "string"));
        actionProps.set("keywords", objectMapper.createObjectNode()
                .put("type", "array")
                .set("items", objectMapper.createObjectNode().put("type", "string")));
        actionProps.set("question", objectMapper.createObjectNode().put("type", "string"));
        actionItem.putArray("required").add("type").add("reason");

        propertiesNode.set("actions", objectMapper.createObjectNode()
                .put("type", "array")
                .set("items", actionItem));

        propertiesNode.set("warnings", objectMapper.createObjectNode()
                .put("type", "array")
                .set("items", objectMapper.createObjectNode().put("type", "string")));

        root.putArray("required").add("assistantMessage");
        return root;
    }

    CoachChatPayload parsePayloadFromGeminiResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String outputText = geminiApiSupport.extractOutputText(root);
        if (outputText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini returned no structured text output.");
        }

        JsonNode payload = objectMapper.readTree(outputText);
        String assistantMessage = payload.path("assistantMessage").asText("").trim();
        List<String> warnings = new ArrayList<>();
        if (payload.path("warnings").isArray()) {
            for (JsonNode w : payload.path("warnings")) {
                String value = w.asText("").trim();
                if (!value.isBlank()) warnings.add(value);
            }
        }

        List<CoachAction> actions = new ArrayList<>();
        JsonNode actionsNode = payload.path("actions");
        if (actionsNode.isArray()) {
            for (JsonNode a : actionsNode) {
                actions.add(new CoachAction(
                        a.path("type").asText(""),
                        a.hasNonNull("bulletIndex") ? a.path("bulletIndex").asInt() : null,
                        a.path("suggestedText").asText(null),
                        a.path("reason").asText(""),
                        readStringArray(a.path("keywords")),
                        a.path("question").asText(null),
                        null,
                        null
                ));
            }
        }

        return new CoachChatPayload(assistantMessage, actions, warnings);
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) values.add(value);
        }
        return values.isEmpty() ? null : values;
    }

    // Error handling and structured output extraction are centralized in GeminiApiSupport.

    public record CoachChatPayload(
            String assistantMessage,
            List<CoachAction> actions,
            List<String> warnings
    ) {
    }
}
