package com.rajan.resumetailor.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rajan.resumetailor.config.GeminiProperties;
import com.rajan.resumetailor.dto.AtsInsights;
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
public class GeminiLatexInsightsClient implements LatexInsightsClient {

    private static final String SYSTEM_INSTRUCTIONS = """
            You are an expert resume reviewer.
            You will be given a job description and a resume bullet list extracted from LaTeX.
            Tasks:
            1) Provide an ATS-style scoring summary and keyword heatmap.
            2) Provide short, practical improvement suggestions for the resume.

            Rules:
            - Only use keywords from the provided candidate keyword list.
            - Do not invent experience, employers, titles, tools, metrics, certifications, or timelines.
            - If a keyword is missing, do not suggest adding it unless it is clearly supported by existing bullets.
              Otherwise, leave suggestionBulletIds empty.
            - Heatmap must reference only bullet ids that are provided (B1..Bn).
            - Return JSON that exactly matches the provided schema.
            """;

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final GeminiApiSupport geminiApiSupport;
    private final AiCallExecutor aiCallExecutor;
    private final GeminiApiKeyResolver geminiApiKeyResolver;

    public GeminiLatexInsightsClient(
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

    @Override
    public LatexInsightsResult getInsights(LatexInsightsRequest request) {
        String apiKey = geminiApiKeyResolver.resolveForCurrentRequest();

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(buildGenerateContentUri())
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(properties.getTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(request), StandardCharsets.UTF_8))
                    .build();

            return aiCallExecutor.execute("gemini.latexInsights", () -> {
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 400) {
                    throw geminiApiSupport.buildGeminiException(objectMapper, response.body(), response.statusCode());
                }
                return parsePayloadFromGeminiResponse(response.body());
            });
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request failed: " + exception.getMessage(), exception);
        }
    }

    URI buildGenerateContentUri() {
        return geminiApiSupport.buildGenerateContentUri(properties.getApiBaseUrl(), properties.getModel());
    }

    String buildRequestBody(LatexInsightsRequest request) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode systemInstruction = root.putObject("system_instruction");
        systemInstruction.putArray("parts").addObject().put("text", SYSTEM_INSTRUCTIONS);

        root.putArray("contents")
                .addObject()
                .putArray("parts")
                .addObject()
                .put("text", buildUserPrompt(request));

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseJsonSchema", buildSchema());

        return objectMapper.writeValueAsString(root);
    }

    String buildUserPrompt(LatexInsightsRequest request) {
        String jd = request.jobDescription() == null ? "" : request.jobDescription().trim();
        List<String> bullets = request.bulletPlainText() == null ? List.of() : request.bulletPlainText();
        List<String> candidateKeywords = request.candidateKeywords() == null ? List.of() : request.candidateKeywords();
        List<String> allowedKeywords = request.allowedKeywords() == null ? List.of() : request.allowedKeywords();

        StringBuilder prompt = new StringBuilder();
        prompt.append("Job description:\n").append(jd).append("\n\n");

        if (!candidateKeywords.isEmpty()) {
            prompt.append("Candidate keywords (only use these keywords; do not introduce other keywords):\n");
            for (String kw : candidateKeywords) {
                if (kw != null && !kw.isBlank()) {
                    prompt.append("- ").append(kw.trim()).append('\n');
                }
            }
            prompt.append('\n');
        }

        if (!allowedKeywords.isEmpty()) {
            prompt.append("Keywords already present in the resume (prefer these over missing keywords; avoid keyword stuffing):\n");
            for (String kw : allowedKeywords) {
                if (kw != null && !kw.isBlank()) {
                    prompt.append("- ").append(kw.trim()).append('\n');
                }
            }
            prompt.append('\n');
        }

        prompt.append("Resume bullets (id: text):\n");
        for (int i = 0; i < bullets.size(); i++) {
            String id = "B" + (i + 1);
            String text = bullets.get(i) == null ? "" : bullets.get(i).trim();
            if (text.isBlank()) {
                continue;
            }
            prompt.append(id).append(": ").append(text).append('\n');
        }
        prompt.append("\nReturn ATS insights and suggestions as JSON.\n");
        return prompt.toString();
    }

    private ObjectNode buildSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);

        ObjectNode props = root.putObject("properties");

        // ATS insights
        ObjectNode ats = props.putObject("atsInsights");
        ats.put("type", "object");
        ats.put("additionalProperties", false);
        ObjectNode atsProps = ats.putObject("properties");
        atsProps.putObject("overallScore").put("type", "integer");

        ObjectNode atsKeywordItem = objectMapper.createObjectNode();
        atsKeywordItem.put("type", "object");
        atsKeywordItem.put("additionalProperties", false);
        ObjectNode akProps = atsKeywordItem.putObject("properties");
        akProps.putObject("keyword").put("type", "string");
        akProps.putObject("present").put("type", "boolean");
        akProps.putObject("evidenceBulletIds").put("type", "array").putObject("items").put("type", "string");
        akProps.putObject("suggestionBulletIds").put("type", "array").putObject("items").put("type", "string");
        atsKeywordItem.putArray("required").add("keyword").add("present").add("evidenceBulletIds").add("suggestionBulletIds");

        ObjectNode mustHave = atsProps.putObject("mustHave");
        mustHave.put("type", "array");
        mustHave.set("items", atsKeywordItem);

        ObjectNode niceToHave = atsProps.putObject("niceToHave");
        niceToHave.put("type", "array");
        niceToHave.set("items", atsKeywordItem);

        ObjectNode bulletHeatItem = objectMapper.createObjectNode();
        bulletHeatItem.put("type", "object");
        bulletHeatItem.put("additionalProperties", false);
        ObjectNode bhProps = bulletHeatItem.putObject("properties");
        bhProps.putObject("bulletId").put("type", "string");
        bhProps.putObject("matchedKeywords").put("type", "array").putObject("items").put("type", "string");
        bulletHeatItem.putArray("required").add("bulletId").add("matchedKeywords");

        ObjectNode heatmap = atsProps.putObject("bulletHeatmap");
        heatmap.put("type", "array");
        heatmap.set("items", bulletHeatItem);

        atsProps.putObject("notes").put("type", "array").putObject("items").put("type", "string");
        ats.putArray("required").add("overallScore").add("mustHave").add("niceToHave").add("bulletHeatmap").add("notes");

        props.putObject("generalSuggestions").put("type", "array").putObject("items").put("type", "string");
        props.putObject("warnings").put("type", "array").putObject("items").put("type", "string");

        root.putArray("required").add("atsInsights").add("generalSuggestions").add("warnings");
        return root;
    }

    LatexInsightsResult parsePayloadFromGeminiResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String outputText = geminiApiSupport.extractOutputText(root);
        if (outputText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini returned no structured text output.");
        }

        GeminiLatexInsightsPayload payload = objectMapper.readValue(outputText, GeminiLatexInsightsPayload.class);
        return sanitize(payload);
    }

    private LatexInsightsResult sanitize(GeminiLatexInsightsPayload payload) {
        AtsInsights ats = payload == null ? null : payload.atsInsights();

        List<String> suggestions = payload == null || payload.generalSuggestions() == null ? List.of() : payload.generalSuggestions();
        suggestions = sanitizeStringList(suggestions, 12);

        List<String> warnings = payload == null || payload.warnings() == null ? List.of() : sanitizeStringList(payload.warnings(), 12);

        return new LatexInsightsResult(ats, suggestions, warnings);
    }

    private List<String> sanitizeStringList(List<String> values, int limit) {
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v == null) continue;
            String t = v.trim();
            if (t.isBlank()) continue;
            out.add(t);
            if (out.size() >= limit) break;
        }
        return out;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeminiLatexInsightsPayload(
            AtsInsights atsInsights,
            List<String> generalSuggestions,
            List<String> warnings
    ) {
    }
}
