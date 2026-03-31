package com.rajan.resumetailor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GeminiApiSupport {

    URI buildGenerateContentUri(URI apiBaseUrl, String model) {
        String encodedModel = URLEncoder.encode(model == null ? "" : model, StandardCharsets.UTF_8);
        String baseUrl = apiBaseUrl == null ? "" : apiBaseUrl.toString();
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        return URI.create(baseUrl + encodedModel + ":generateContent");
    }

    String extractOutputText(JsonNode root) {
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

    ResponseStatusException buildGeminiException(ObjectMapper mapper, String responseBody, int statusCode) {
        HttpStatus mappedStatus = switch (statusCode) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };

        String message = "Gemini request failed with an upstream error.";
        try {
            JsonNode root = mapper.readTree(responseBody);
            String upstream = root.path("error").path("message").asText("");
            if (!upstream.isBlank()) {
                message = "Gemini request failed: " + upstream;
            }
        } catch (IOException ignored) {
            if (responseBody != null && !responseBody.isBlank()) {
                String trimmed = responseBody.lines().limit(6).collect(Collectors.joining(" ")).trim();
                if (!trimmed.isBlank()) {
                    message = "Gemini request failed: " + trimmed;
                }
            }
        }

        return new ResponseStatusException(mappedStatus, message);
    }
}

