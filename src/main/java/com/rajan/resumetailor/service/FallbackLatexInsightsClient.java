package com.rajan.resumetailor.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class FallbackLatexInsightsClient implements LatexInsightsClient {

    private final GeminiLatexInsightsClient primary;
    private final DeterministicLatexInsightsClient fallback;
    private final AiFailureClassifier failureClassifier;

    public FallbackLatexInsightsClient(
            GeminiLatexInsightsClient primary,
            DeterministicLatexInsightsClient fallback,
            AiFailureClassifier failureClassifier
    ) {
        this.primary = primary;
        this.fallback = fallback;
        this.failureClassifier = failureClassifier;
    }

    @Override
    public LatexInsightsResult getInsights(LatexInsightsRequest request) {
        try {
            return primary.getInsights(request);
        } catch (Exception exception) {
            if (failureClassifier.isQuotaExhausted(exception)) {
                AiCallWarnings.add("AI insights unavailable due to provider quota; showing heuristic insights.");
                return fallback.getInsights(request);
            }

            AiCallWarnings.add("AI insights unavailable due to a temporary error; showing heuristic insights.");
            return fallback.getInsights(request);
        }
    }
}

