package com.rajan.resumetailor.service;

import com.rajan.resumetailor.dto.KeywordInsights;
import java.util.List;

public record LatexInsightsRequest(
        String resumeLatex,
        List<String> bulletLatex,
        List<String> bulletPlainText,
        String jobDescription,
        KeywordInsights keywordInsights,
        List<String> candidateKeywords,
        List<String> allowedKeywords
) {
}

