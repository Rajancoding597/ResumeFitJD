package com.rajan.resumetailor.service;

import com.rajan.resumetailor.dto.AtsInsights;
import com.rajan.resumetailor.dto.KeywordInsights;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DeterministicLatexInsightsClient implements LatexInsightsClient {

    private final AtsInsightsHeuristicService atsInsightsHeuristicService;

    public DeterministicLatexInsightsClient(AtsInsightsHeuristicService atsInsightsHeuristicService) {
        this.atsInsightsHeuristicService = atsInsightsHeuristicService;
    }

    @Override
    public LatexInsightsResult getInsights(LatexInsightsRequest request) {
        AtsInsights ats = atsInsightsHeuristicService.buildFromLatex(
                request.resumeLatex(),
                request.bulletLatex(),
                request.jobDescription()
        );
        List<String> suggestions = heuristicSuggestions(request.keywordInsights());
        return new LatexInsightsResult(ats, suggestions, List.of());
    }

    private List<String> heuristicSuggestions(KeywordInsights keywordInsights) {
        List<String> suggestions = new ArrayList<>();
        if (keywordInsights != null) {
            int coverage = Math.max(0, Math.min(100, keywordInsights.coveragePercent()));
            if (coverage < 35) {
                suggestions.add("Low keyword coverage: add 1-2 truthful bullets that directly match the job's core requirements.");
            } else if (coverage < 60) {
                suggestions.add("Moderate keyword coverage: tighten wording so key tools and responsibilities are easy to find at a glance.");
            }
            if (keywordInsights.missingKeywords() != null && !keywordInsights.missingKeywords().isEmpty()) {
                suggestions.add("If you truly have experience with any missing keywords, add evidence in bullets (project + impact), not just a skills list.");
            }
        }
        suggestions.add("Prefer: action verb + what you built + scale/impact + tech. Keep bullets 1 line when possible.");
        return suggestions.size() > 8 ? suggestions.subList(0, 8) : suggestions;
    }
}

