package com.rajan.resumetailor.dto;

import java.util.List;

public record AiDocTailorResponse(
        ExtractedResume extracted,
        List<ResumeChange> changes,
        KeywordInsights keywordInsights,
        AtsInsights atsInsights,
        List<String> generalSuggestions,
        List<String> warnings
) {
}
