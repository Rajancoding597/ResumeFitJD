package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rajan.resumetailor.dto.AtsInsights;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FallbackLatexInsightsClientTest {

    @Test
    void fallsBackOnQuotaExhaustionAndAddsWarning() {
        AiCallWarnings.clear();

        GeminiLatexInsightsClient primary = new GeminiLatexInsightsClient(
                new com.rajan.resumetailor.config.GeminiProperties(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                java.net.http.HttpClient.newHttpClient(),
                new GeminiApiSupport(),
                new AiCallExecutor(new com.rajan.resumetailor.config.AiExecutionProperties(), new AiFailureClassifier()),
                new GeminiApiKeyResolver(new com.rajan.resumetailor.config.GeminiProperties(), false)
        ) {
            @Override
            public LatexInsightsResult getInsights(LatexInsightsRequest request) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "quota exhausted");
            }
        };

        DeterministicLatexInsightsClient fallback = new DeterministicLatexInsightsClient(
                new AtsInsightsHeuristicService(new JobKeywordExtractor(), new LatexPlainTextExtractor(), new KeywordMatcher())
        ) {
            @Override
            public LatexInsightsResult getInsights(LatexInsightsRequest request) {
                return new LatexInsightsResult(new AtsInsights(10, List.of(), List.of(), List.of(), List.of()), List.of("fallback"), List.of());
            }
        };

        FallbackLatexInsightsClient router = new FallbackLatexInsightsClient(primary, fallback, new AiFailureClassifier());
        LatexInsightsResult result = router.getInsights(new LatexInsightsRequest("", List.of(), List.of(), "", null, List.of(), List.of()));

        assertEquals(10, result.atsInsights().overallScore());
        assertTrue(AiCallWarnings.drain().stream().anyMatch(w -> w.toLowerCase().contains("quota")));
    }
}
