package com.rajan.resumetailor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rajan.resumetailor.config.GeminiProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GeminiResumeRewriterClient implements ResumeRewriterClient {

    private static final Pattern NUMBER_CLAIM = Pattern.compile("(?i)\\b\\d[\\d,]*(?:\\.\\d+)?\\s*(?:%|x|×|\\\\%)?\\b");
    private static final Pattern TECH_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9+#./\\-]{1,}");

    private static final String SYSTEM_INSTRUCTIONS = """
            You rewrite resume bullet points for a specific job description.
            Rules:
            - Keep every rewrite strictly truthful to the original bullet.
            - Do not invent employers, titles, tools, metrics, achievements, certifications, or years of experience.
            - Preserve the original meaning while improving relevance, specificity, and readability.
            - Preserve any numbers/metrics/tools/entities already present; do not add new ones.
            - Sound natural and professional, not robotic, exaggerated, or keyword-stuffed.
            - Keep each rewrite concise and resume-appropriate.
            - Keep the same order and number of bullets.
            - Return JSON that exactly matches the provided schema.
            """;

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final GeminiApiSupport geminiApiSupport;

    public GeminiResumeRewriterClient(GeminiProperties properties, ObjectMapper objectMapper, HttpClient httpClient, GeminiApiSupport geminiApiSupport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.geminiApiSupport = geminiApiSupport;
    }

    @Override
    public List<RewriteSuggestion> rewriteBullets(RewriteBulletsRequest rewriteRequest) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured. Add it to .env before using the app.");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(buildGenerateContentUri())
                    .header("x-goog-api-key", properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(properties.getTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(rewriteRequest), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw geminiApiSupport.buildGeminiException(objectMapper, response.body(), response.statusCode());
            }

            return extractSuggestions(response.body());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request was interrupted.", exception);
        }
    }

    private URI buildGenerateContentUri() {
        return geminiApiSupport.buildGenerateContentUri(properties.getApiBaseUrl(), properties.getModel());
    }

    String buildRequestBody(RewriteBulletsRequest rewriteRequest) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode systemInstruction = root.putObject("system_instruction");
        systemInstruction.putArray("parts")
                .addObject()
                .put("text", SYSTEM_INSTRUCTIONS);

        root.putArray("contents")
                .addObject()
                .putArray("parts")
                .addObject()
                .put("text", buildUserPrompt(rewriteRequest));

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseJsonSchema", buildSchema());

        return objectMapper.writeValueAsString(root);
    }

    JsonNode buildSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);

        ObjectNode propertiesNode = root.putObject("properties");
        ObjectNode rewrites = propertiesNode.putObject("rewrites");
        rewrites.put("type", "array");

        ObjectNode item = rewrites.putObject("items");
        item.put("type", "object");
        item.put("additionalProperties", false);
        ObjectNode itemProperties = item.putObject("properties");
        itemProperties.putObject("revisedBullet")
                .put("type", "string")
                .put("description", "A truthful, more job-aligned rewrite of the original resume bullet.");
        itemProperties.putObject("reason")
                .put("type", "string")
                .put("description", "A short explanation of why the rewritten bullet better matches the job.");
        ObjectNode stringArray = objectMapper.createObjectNode()
                .put("type", "array")
                .set("items", objectMapper.createObjectNode().put("type", "string"));

        itemProperties.set("targetKeywordsUsed", stringArray.deepCopy()
                .put("description", "Optional: which provided target keywords were incorporated (if any)."));
        itemProperties.set("preservedClaims", stringArray.deepCopy()
                .put("description", "Optional: which original metrics/tools/entities were preserved (if any)."));
        item.putArray("required")
                .add("revisedBullet")
                .add("reason");

        root.putArray("required").add("rewrites");
        return root;
    }

    String buildUserPrompt(RewriteBulletsRequest rewriteRequest) {
        List<String> originalBullets = rewriteRequest.originalBullets() == null ? List.of() : rewriteRequest.originalBullets();
        String jobDescription = rewriteRequest.jobDescription() == null ? "" : rewriteRequest.jobDescription();
        List<String> allowedKeywords = rewriteRequest.allowedKeywords() == null ? List.of() : rewriteRequest.allowedKeywords();

        StringBuilder prompt = new StringBuilder();
        prompt.append("Job description:\n")
                .append(jobDescription.trim())
                .append("\n\nOriginal resume bullets:\n");

        if (!allowedKeywords.isEmpty()) {
            prompt.append("\nAllowed keywords (use sparingly, only if they are already supported by the original resume; avoid keyword stuffing):\n");
            for (int i = 0; i < allowedKeywords.size(); i++) {
                prompt.append("- ").append(allowedKeywords.get(i)).append('\n');
            }
            prompt.append('\n');
        }

        for (int index = 0; index < originalBullets.size(); index++) {
            String bullet = originalBullets.get(index) == null ? "" : originalBullets.get(index).trim();
            List<String> protectedClaims = collectProtectedClaims(bullet);

            prompt.append("Bullet ")
                    .append(index + 1)
                    .append(":\n")
                    .append("Original: ")
                    .append(bullet)
                    .append('\n');

            if (!protectedClaims.isEmpty()) {
                prompt.append("Protected claims to preserve (numbers/tools/entities; do not add new claims): ");
                for (int i = 0; i < protectedClaims.size(); i++) {
                    if (i > 0) {
                        prompt.append(", ");
                    }
                    prompt.append(protectedClaims.get(i));
                }
                prompt.append('\n');
            }

            prompt.append("Rewrite constraints: keep meaning, keep tense, keep metrics/entities, prefer natural phrasing over keyword stuffing.\n\n");
        }

        prompt.append("Return one rewrite and one short reason for each bullet, keeping the same bullet order and count.\n");
        prompt.append("If a keyword is not truly supported by the original bullet/resume, do not introduce it.\n");
        return prompt.toString();
    }

    List<RewriteSuggestion> extractSuggestions(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String outputText = geminiApiSupport.extractOutputText(root);
        if (outputText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini returned no structured text output.");
        }

        JsonNode rewriteRoot = objectMapper.readTree(outputText);
        JsonNode rewrites = rewriteRoot.path("rewrites");
        if (!rewrites.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini returned malformed rewrite data.");
        }

        List<RewriteSuggestion> suggestions = new ArrayList<>();
        for (JsonNode rewrite : rewrites) {
            suggestions.add(new RewriteSuggestion(
                    rewrite.path("revisedBullet").asText(""),
                    rewrite.path("reason").asText(""),
                    readOptionalStringArray(rewrite.path("targetKeywordsUsed")),
                    readOptionalStringArray(rewrite.path("preservedClaims"))
            ));
        }
        return suggestions;
    }

    private List<String> readOptionalStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values.isEmpty() ? null : values;
    }

    private List<String> collectProtectedClaims(String bullet) {
        if (bullet == null || bullet.isBlank()) {
            return List.of();
        }

        Set<String> claims = new HashSet<>();
        Matcher numberMatcher = NUMBER_CLAIM.matcher(bullet);
        while (numberMatcher.find()) {
            String value = numberMatcher.group().trim();
            if (!value.isBlank()) {
                claims.add(value);
            }
        }

        Matcher tokenMatcher = TECH_TOKEN.matcher(bullet);
        while (tokenMatcher.find()) {
            String token = tokenMatcher.group();
            if (token.length() < 2) {
                continue;
            }

            boolean looksTechnical = false;
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                if (Character.isUpperCase(c) || Character.isDigit(c) || c == '+' || c == '#' || c == '.' || c == '/' || c == '-') {
                    looksTechnical = true;
                    break;
                }
            }
            if (!looksTechnical) {
                continue;
            }

            // Avoid adding common leading punctuation-only tokens.
            String cleaned = token.trim();
            if (!cleaned.isBlank()) {
                claims.add(cleaned);
            }
        }

        return claims.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(12)
                .toList();
    }

    // Error handling is centralized in GeminiApiSupport.
}
