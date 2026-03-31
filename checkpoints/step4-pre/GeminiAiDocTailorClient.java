package com.rajan.resumetailor.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rajan.resumetailor.config.GeminiProperties;
import com.rajan.resumetailor.dto.ExtractedBullet;
import com.rajan.resumetailor.dto.ExtractedResume;
import com.rajan.resumetailor.dto.KeywordInsights;
import com.rajan.resumetailor.dto.AtsInsights;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@Component
public class GeminiAiDocTailorClient {

    private static final String SYSTEM_INSTRUCTIONS = """
            You are an expert resume reviewer.
            You will be given a resume as a PDF or image, and a job description.
            Tasks:
            1) Extract resume bullet points (only what is visible; do not invent).
            2) Rewrite each bullet to better match the job description while staying strictly truthful.
            3) Provide keyword insights (matched/missing) based on the resume content and job description.
            4) Provide an ATS-style scoring summary and keyword heatmap.
            5) Provide short, practical improvement suggestions for the resume.

            Rules:
            - Do not invent experience, employers, titles, tools, metrics, certifications, or timelines.
            - Do not change numeric claims; keep numbers exactly as in the resume.
            - Keep language natural and professional.
            - For ATS insights: separate keywords into must-have vs nice-to-have, and reference bullet ids for evidence/suggestions.
            - Return JSON that exactly matches the provided schema.
            """;

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final GeminiApiSupport geminiApiSupport;
    private final AiCallExecutor aiCallExecutor;

    public GeminiAiDocTailorClient(
            GeminiProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            GeminiApiSupport geminiApiSupport,
            AiCallExecutor aiCallExecutor
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.geminiApiSupport = geminiApiSupport;
        this.aiCallExecutor = aiCallExecutor;
    }

    public AiDocTailorPayload tailorFromDocument(MultipartFile resumeFile, String contentType, String jobDescription, AiDocOptions options) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured. Add it to .env before using the app.");
        }

        try {
            byte[] bytes = resumeFile.getBytes();
            HttpRequest request = HttpRequest.newBuilder(buildGenerateContentUri())
                    .header("x-goog-api-key", properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(properties.getTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(bytes, contentType, jobDescription, options), StandardCharsets.UTF_8))
                    .build();

            return aiCallExecutor.execute("gemini.aiDocTailor", () -> {
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

    URI buildGenerateContentUri() {
        return geminiApiSupport.buildGenerateContentUri(properties.getApiBaseUrl(), properties.getModel());
    }

    String buildRequestBody(byte[] fileBytes, String contentType, String jobDescription, AiDocOptions options) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode systemInstruction = root.putObject("system_instruction");
        systemInstruction.putArray("parts")
                .addObject()
                .put("text", SYSTEM_INSTRUCTIONS);

        ObjectNode content = root.putArray("contents").addObject();
        ArrayNode parts = content.putArray("parts");

        ObjectNode inlineData = parts.addObject().putObject("inline_data");
        inlineData.put("mime_type", contentType);
        inlineData.put("data", Base64.getEncoder().encodeToString(fileBytes));

        parts.addObject().put("text", buildUserPrompt(jobDescription, options));

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseJsonSchema", buildSchema());

        return objectMapper.writeValueAsString(root);
    }

    private String buildUserPrompt(String jobDescription, AiDocOptions options) {
        int maxBullets = options == null ? AiDocOptions.defaults().maxBullets() : options.maxBullets();
        String tone = options == null ? AiDocOptions.defaults().tone() : options.tone();

        return """
                Job description:
                %s

                Output requirements:
                - Extract up to %d bullets from the resume.
                - Use stable bullet ids: b1, b2, b3, ...
                - Tone: %s
                """.formatted(jobDescription.trim(), maxBullets, tone);
    }

    private JsonNode buildSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);

        ObjectNode props = root.putObject("properties");

        ObjectNode extracted = props.putObject("extracted");
        extracted.put("type", "object");
        extracted.put("additionalProperties", false);
        ObjectNode extractedProps = extracted.putObject("properties");
        extractedProps.putObject("rawTextPreview").put("type", "string");
        ObjectNode bullets = extractedProps.putObject("bullets");
        bullets.put("type", "array");
        ObjectNode bulletItem = bullets.putObject("items");
        bulletItem.put("type", "object");
        bulletItem.put("additionalProperties", false);
        ObjectNode bulletItemProps = bulletItem.putObject("properties");
        bulletItemProps.putObject("id").put("type", "string");
        bulletItemProps.putObject("section").put("type", "string");
        bulletItemProps.putObject("originalBullet").put("type", "string");
        bulletItem.putArray("required").add("id").add("originalBullet");
        extracted.putArray("required").add("bullets");

        ObjectNode tailored = props.putObject("tailored");
        tailored.put("type", "array");
        ObjectNode tailoredItem = tailored.putObject("items");
        tailoredItem.put("type", "object");
        tailoredItem.put("additionalProperties", false);
        ObjectNode tailoredProps = tailoredItem.putObject("properties");
        tailoredProps.putObject("id").put("type", "string");
        tailoredProps.putObject("revisedBullet").put("type", "string");
        tailoredProps.putObject("reason").put("type", "string");
        tailoredItem.putArray("required").add("id").add("revisedBullet").add("reason");

        ObjectNode keywordInsights = props.putObject("keywordInsights");
        keywordInsights.put("type", "object");
        keywordInsights.put("additionalProperties", false);
        ObjectNode kiProps = keywordInsights.putObject("properties");
        kiProps.putObject("coveragePercent").put("type", "integer");
        kiProps.putObject("matchedKeywords").put("type", "array").putObject("items").put("type", "string");
        kiProps.putObject("missingKeywords").put("type", "array").putObject("items").put("type", "string");
        ObjectNode topKeywords = kiProps.putObject("topKeywords");
        topKeywords.put("type", "array");
        ObjectNode tkItem = topKeywords.putObject("items");
        tkItem.put("type", "object");
        tkItem.put("additionalProperties", false);
        ObjectNode tkProps = tkItem.putObject("properties");
        tkProps.putObject("keyword").put("type", "string");
        tkProps.putObject("weight").put("type", "number");
        tkProps.putObject("inResume").put("type", "boolean");
        tkItem.putArray("required").add("keyword").add("weight").add("inResume");
        kiProps.putObject("notes").put("type", "array").putObject("items").put("type", "string");
        keywordInsights.putArray("required").add("coveragePercent").add("matchedKeywords").add("missingKeywords");

        props.putObject("generalSuggestions").put("type", "array").putObject("items").put("type", "string");
        props.putObject("warnings").put("type", "array").putObject("items").put("type", "string");

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

        root.putArray("required")
                .add("extracted")
                .add("tailored")
                .add("keywordInsights")
                .add("atsInsights")
                .add("generalSuggestions")
                .add("warnings");
        return root;
    }

    AiDocTailorPayload parsePayloadFromGeminiResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String outputText = geminiApiSupport.extractOutputText(root);
        if (outputText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini returned no structured text output.");
        }
        return objectMapper.readValue(outputText, AiDocTailorPayload.class);
    }

    // Error handling and structured output extraction are centralized in GeminiApiSupport.

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiDocTailorPayload(
            ExtractedResume extracted,
            List<TailoredBullet> tailored,
            KeywordInsights keywordInsights,
            AtsInsights atsInsights,
            List<String> generalSuggestions,
            List<String> warnings
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TailoredBullet(
            String id,
            String revisedBullet,
            String reason
    ) {
    }
}
