package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajan.resumetailor.config.GeminiProperties;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiLatexInsightsClientTest {

    @Test
    void buildsSchemaAndUsesJsonMimeType() throws Exception {
        GeminiProperties props = new GeminiProperties();
        props.setApiKey("dummy");
        ObjectMapper mapper = new ObjectMapper();
        GeminiLatexInsightsClient client = new GeminiLatexInsightsClient(
                props,
                mapper,
                HttpClient.newHttpClient(),
                new GeminiApiSupport(),
                new AiCallExecutor(new com.rajan.resumetailor.config.AiExecutionProperties(), new AiFailureClassifier()),
                new GeminiApiKeyResolver(props, false)
        );

        String body = client.buildRequestBody(new LatexInsightsRequest(
                "\\\\begin{itemize}\\\\item Built services.\\\\end{itemize}",
                List.of("\\\\item Built services."),
                List.of("Built services."),
                "Need Java services.",
                null,
                List.of("java", "services"),
                List.of("services")
        ));
        JsonNode root = mapper.readTree(body);
        assertEquals("application/json", root.path("generationConfig").path("responseMimeType").asText());
        assertTrue(root.path("generationConfig").has("responseJsonSchema"));
        assertTrue(root.path("system_instruction").has("parts"));
    }

    @Test
    void parsesPayloadFromGeminiResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        GeminiProperties props = new GeminiProperties();
        props.setApiKey("dummy");
        GeminiLatexInsightsClient client = new GeminiLatexInsightsClient(
                props,
                mapper,
                HttpClient.newHttpClient(),
                new GeminiApiSupport(),
                new AiCallExecutor(new com.rajan.resumetailor.config.AiExecutionProperties(), new AiFailureClassifier()),
                new GeminiApiKeyResolver(props, false)
        );

        String payload = """
                {
                  "atsInsights": {
                    "overallScore": 62,
                    "mustHave": [
                      {"keyword":"java","present":true,"evidenceBulletIds":["B1"],"suggestionBulletIds":[]}
                    ],
                    "niceToHave": [],
                    "bulletHeatmap": [
                      {"bulletId":"B1","matchedKeywords":["java"]}
                    ],
                    "notes": ["AI ATS summary."]
                  },
                  "generalSuggestions": ["Add impact where possible."],
                  "warnings": ["Heuristic; verify claims."]
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

        LatexInsightsResult parsed = client.parsePayloadFromGeminiResponse(geminiResponse);
        assertEquals(62, parsed.atsInsights().overallScore());
        assertEquals("Add impact where possible.", parsed.generalSuggestions().get(0));
        assertEquals(1, parsed.warnings().size());
    }
}
