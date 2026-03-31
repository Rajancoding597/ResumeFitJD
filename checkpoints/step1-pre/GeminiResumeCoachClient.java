package com.rajan.resumetailor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rajan.resumetailor.config.GeminiProperties;
import com.rajan.resumetailor.dto.CoachAction;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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

    public GeminiResumeCoachClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    public CoachChatPayload chat(String prompt) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured. Add it to .env before using the app.");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(buildGenerateContentUri())
                    .header("x-goog-api-key", properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(properties.getTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(prompt), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw buildGeminiException(response.body(), response.statusCode());
            }

            return parsePayloadFromGeminiResponse(response.body());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request was interrupted.", exception);
        }
    }

    private URI buildGenerateContentUri() {
        String encodedModel = URLEncoder.encode(properties.getModel(), StandardCharsets.UTF_8);
        String baseUrl = properties.getApiBaseUrl().toString();
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        return URI.create(baseUrl + encodedModel + ":generateContent");
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
        String outputText = extractOutputText(root);
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

    private String extractOutputText(JsonNode root) {
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode candidate : candidates) {
            JsonNode parts = candidate.path("content").path("parts");
            if (!parts.isArray()) {
                continue;
            }
            for (JsonNode part : parts) {
                String text = part.path("text").asText("");
                if (!text.isBlank()) {
                    builder.append(text);
                }
            }
        }
        return builder.toString().trim();
    }

    private ResponseStatusException buildGeminiException(String responseBody, int statusCode) {
        String message = "Gemini request failed.";
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            String status = error.path("status").asText("");
            String text = error.path("message").asText("");
            if (!text.isBlank()) {
                message = text;
            } else if (!status.isBlank()) {
                message = status;
            }
        } catch (Exception ignored) {
            if (responseBody != null && !responseBody.isBlank()) {
                message = responseBody;
            }
        }

        HttpStatus httpStatus = statusCode >= 500 ? HttpStatus.BAD_GATEWAY : HttpStatus.BAD_REQUEST;
        String trimmed = message.lines().limit(6).collect(Collectors.joining(" ")).trim();
        if (trimmed.isBlank()) {
            trimmed = "Gemini request failed.";
        }
        return new ResponseStatusException(httpStatus, "Gemini request failed: " + trimmed);
    }

    public record CoachChatPayload(
            String assistantMessage,
            List<CoachAction> actions,
            List<String> warnings
    ) {
    }
}
