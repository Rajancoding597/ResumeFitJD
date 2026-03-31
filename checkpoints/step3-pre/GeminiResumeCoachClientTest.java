package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajan.resumetailor.config.GeminiProperties;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class GeminiResumeCoachClientTest {

    @Test
    void parsesAssistantMessageAndActions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        GeminiProperties props = new GeminiProperties();
        props.setApiKey("dummy");
        GeminiResumeCoachClient client = new GeminiResumeCoachClient(props, mapper, HttpClient.newHttpClient(), new GeminiApiSupport());

        String payload = """
                {
                  "assistantMessage": "Here are two safe improvements.",
                  "actions": [
                    {"type":"edit_bullet","bulletIndex":0,"suggestedText":"Improved performance for internal users.","reason":"Clarifies scope."},
                    {"type":"pin_keywords","keywords":["java","aws"],"reason":"Track these during review."},
                    {"type":"ask_user","question":"Did you actually use Kafka in this role?","reason":"Avoid inventing skills."}
                  ],
                  "warnings": ["Heuristic suggestions."]
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
        assertEquals("Here are two safe improvements.", parsed.assistantMessage());
        assertNotNull(parsed.actions());
        assertEquals(3, parsed.actions().size());
        assertEquals("edit_bullet", parsed.actions().get(0).type());
        assertEquals(1, parsed.warnings().size());
    }
}
