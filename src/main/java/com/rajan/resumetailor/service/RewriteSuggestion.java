package com.rajan.resumetailor.service;

import java.util.List;

public record RewriteSuggestion(
        String revisedBullet,
        String reason,
        List<String> targetKeywordsUsed,
        List<String> preservedClaims
) {

    public RewriteSuggestion(String revisedBullet, String reason) {
        this(revisedBullet, reason, null, null);
    }
}
