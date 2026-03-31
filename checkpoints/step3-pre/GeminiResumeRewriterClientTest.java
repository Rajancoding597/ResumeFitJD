package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajan.resumetailor.config.GeminiProperties;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiResumeRewriterClientTest {

    @Test
    void parsesOptionalMetadataFieldsWhenPresent() throws Exception {
        GeminiProperties props = new GeminiProperties();
        GeminiResumeRewriterClient client = new GeminiResumeRewriterClient(props, new ObjectMapper(), HttpClient.newHttpClient(), new GeminiApiSupport());

        String responseBody = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"rewrites\\":[{\\"revisedBullet\\":\\"Built APIs for internal users.\\",\\"reason\\":\\"Clarifies scope.\\",\\"targetKeywordsUsed\\":[\\"api\\",\\"internal\\"],\\"preservedClaims\\":[\\"APIs\\"]}]}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        List<RewriteSuggestion> suggestions = client.extractSuggestions(responseBody);
        assertEquals(1, suggestions.size());
        assertEquals("Built APIs for internal users.", suggestions.get(0).revisedBullet());
        assertEquals("Clarifies scope.", suggestions.get(0).reason());
        assertNotNull(suggestions.get(0).targetKeywordsUsed());
        assertEquals(List.of("api", "internal"), suggestions.get(0).targetKeywordsUsed());
        assertEquals(List.of("APIs"), suggestions.get(0).preservedClaims());
    }

    @Test
    void userPromptIncludesBulletIndexAndAllowedKeywords() {
        GeminiProperties props = new GeminiProperties();
        GeminiResumeRewriterClient client = new GeminiResumeRewriterClient(props, new ObjectMapper(), HttpClient.newHttpClient(), new GeminiApiSupport());

        String prompt = client.buildUserPrompt(new RewriteBulletsRequest(
                List.of("Built internal dashboards for delivery metrics."),
                "Need someone with dashboards and metrics experience.",
                List.of("dashboards", "metrics")
        ));

        assertTrue(prompt.contains("Allowed keywords"));
        assertTrue(prompt.contains("Bullet 1:"));
        assertTrue(prompt.contains("dashboards"));
        assertTrue(prompt.contains("metrics"));
    }
}
