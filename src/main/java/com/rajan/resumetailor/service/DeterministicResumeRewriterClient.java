package com.rajan.resumetailor.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DeterministicResumeRewriterClient {

    public List<RewriteSuggestion> keepOriginalBullets(RewriteBulletsRequest request, String reason) {
        List<String> originals = request.originalBullets() == null ? List.of() : request.originalBullets();
        String safeReason = (reason == null || reason.isBlank())
                ? "Original bullet kept because the AI provider is unavailable."
                : reason.trim();

        List<RewriteSuggestion> suggestions = new ArrayList<>(originals.size());
        for (String bullet : originals) {
            String original = bullet == null ? "" : bullet;
            suggestions.add(new RewriteSuggestion(original, safeReason));
        }
        return suggestions;
    }
}

