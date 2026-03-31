package com.rajan.resumetailor.service;

import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class FallbackResumeRewriterClient implements ResumeRewriterClient {

    private final GeminiResumeRewriterClient primary;
    private final DeterministicResumeRewriterClient deterministicFallback;
    private final AiFailureClassifier failureClassifier;

    public FallbackResumeRewriterClient(
            GeminiResumeRewriterClient primary,
            DeterministicResumeRewriterClient deterministicFallback,
            AiFailureClassifier failureClassifier
    ) {
        this.primary = primary;
        this.deterministicFallback = deterministicFallback;
        this.failureClassifier = failureClassifier;
    }

    @Override
    public List<RewriteSuggestion> rewriteBullets(RewriteBulletsRequest rewriteRequest) {
        try {
            return primary.rewriteBullets(rewriteRequest);
        } catch (Exception exception) {
            if (failureClassifier.isQuotaExhausted(exception)) {
                AiCallWarnings.add("AI provider quota was exhausted; original bullets were kept.");
                return deterministicFallback.keepOriginalBullets(
                        rewriteRequest,
                        "Original bullet kept because the AI provider quota was exhausted."
                );
            }
            throw exception;
        }
    }
}

