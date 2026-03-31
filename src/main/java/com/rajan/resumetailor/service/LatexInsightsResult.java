package com.rajan.resumetailor.service;

import com.rajan.resumetailor.dto.AtsInsights;
import java.util.List;

public record LatexInsightsResult(
        AtsInsights atsInsights,
        List<String> generalSuggestions,
        List<String> warnings
) {
}

