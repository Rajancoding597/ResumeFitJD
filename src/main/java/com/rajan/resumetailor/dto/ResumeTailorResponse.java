package com.rajan.resumetailor.dto;

import java.util.List;

public record ResumeTailorResponse(
        String updatedLatex,
        List<ResumeChange> changes,
        List<String> warnings,
        KeywordInsights keywordInsights,
        AtsInsights atsInsights,
        List<String> generalSuggestions
) {
}
