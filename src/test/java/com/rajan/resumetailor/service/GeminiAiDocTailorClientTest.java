package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajan.resumetailor.config.GeminiProperties;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class GeminiAiDocTailorClientTest {

    @Test
    void buildsInlineDataAndSchema() throws Exception {
        GeminiProperties props = new GeminiProperties();
        props.setApiKey("dummy");
        ObjectMapper mapper = new ObjectMapper();
        GeminiAiDocTailorClient client = new GeminiAiDocTailorClient(
                props,
                mapper,
                HttpClient.newHttpClient(),
                new GeminiApiSupport(),
                new AiCallExecutor(new com.rajan.resumetailor.config.AiExecutionProperties(), new AiFailureClassifier()),
                new GeminiApiKeyResolver(props, false)
        );

        byte[] bytes = "pdf-bytes".getBytes(StandardCharsets.UTF_8);
        String body = client.buildRequestBody(bytes, "application/pdf", "Java AWS Kafka", AiDocOptions.defaults());
        JsonNode root = mapper.readTree(body);

        assertTrue(root.has("system_instruction"));
        assertTrue(root.has("contents"));
        assertEquals("application/json", root.path("generationConfig").path("responseMimeType").asText());

        JsonNode parts = root.path("contents").get(0).path("parts");
        assertEquals("application/pdf", parts.get(0).path("inline_data").path("mime_type").asText());
        assertTrue(parts.get(0).path("inline_data").path("data").asText().length() > 0);
        assertTrue(root.path("generationConfig").has("responseJsonSchema"));
    }

    @Test
    void parsesPayloadFromGeminiResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        GeminiProperties props = new GeminiProperties();
        props.setApiKey("dummy");
        GeminiAiDocTailorClient client = new GeminiAiDocTailorClient(
                props,
                mapper,
                HttpClient.newHttpClient(),
                new GeminiApiSupport(),
                new AiCallExecutor(new com.rajan.resumetailor.config.AiExecutionProperties(), new AiFailureClassifier()),
                new GeminiApiKeyResolver(props, false)
        );

        String payload = """
                {
                  "extracted": {
                    "rawTextPreview": "Preview",
                    "bullets": [
                      {"id":"b1","section":"Experience","originalBullet":"Built Java services."}
                    ]
                  },
                  "tailored": [
                    {"id":"b1","revisedBullet":"Built Java services on AWS.","reason":"Adds AWS relevance."}
                  ],
                  "keywordInsights": {
                    "coveragePercent": 50,
                    "matchedKeywords": ["java"],
                    "missingKeywords": ["aws"],
                    "topKeywords": [],
                    "notes": []
                  },
                  "atsInsights": {
                    "overallScore": 62,
                    "mustHave": [
                      {"keyword":"aws","present":false,"evidenceBulletIds":[],"suggestionBulletIds":["b1"]}
                    ],
                    "niceToHave": [
                      {"keyword":"kafka","present":false,"evidenceBulletIds":[],"suggestionBulletIds":["b1"]}
                    ],
                    "bulletHeatmap": [
                      {"bulletId":"b1","matchedKeywords":["java"]}
                    ],
                    "notes": ["Heuristic ATS score; verify claims."]
                  },
                  "generalSuggestions": ["Add measurable impact."],
                  "warnings": []
                }
                """;

        String geminiResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {"text": %s}
                        ]
                      }
                    }
                  ]
                }
                """.formatted(mapper.writeValueAsString(payload));

        var parsed = client.parsePayloadFromGeminiResponse(geminiResponse);
        assertEquals("Preview", parsed.extracted().rawTextPreview());
        assertEquals("b1", parsed.extracted().bullets().get(0).id());
        assertEquals("Built Java services on AWS.", parsed.tailored().get(0).revisedBullet());
    }
}
