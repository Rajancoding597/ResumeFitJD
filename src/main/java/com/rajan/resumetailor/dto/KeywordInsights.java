package com.rajan.resumetailor.dto;

import java.util.List;

public record KeywordInsights(
        int coveragePercent,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        List<KeywordScore> topKeywords,
        List<String> notes
) {
}

