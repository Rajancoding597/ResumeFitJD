package com.rajan.resumetailor.service;

import com.rajan.resumetailor.dto.AtsInsights;
import com.rajan.resumetailor.dto.AtsKeyword;
import com.rajan.resumetailor.dto.BulletHeat;
import com.rajan.resumetailor.service.JobKeywordExtractor.WeightedKeyword;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AtsInsightsHeuristicService {

    private static final int DEFAULT_LIMIT = 24;
    private static final int MUST_HAVE_COUNT = 10;
    private static final int NICE_TO_HAVE_COUNT = 10;

    private final JobKeywordExtractor jobKeywordExtractor;
    private final LatexPlainTextExtractor latexPlainTextExtractor;
    private final KeywordMatcher keywordMatcher;

    public AtsInsightsHeuristicService(
            JobKeywordExtractor jobKeywordExtractor,
            LatexPlainTextExtractor latexPlainTextExtractor,
            KeywordMatcher keywordMatcher
    ) {
        this.jobKeywordExtractor = jobKeywordExtractor;
        this.latexPlainTextExtractor = latexPlainTextExtractor;
        this.keywordMatcher = keywordMatcher;
    }

    public AtsInsights buildFromLatex(String resumeLatex, List<String> bulletLatex, String jobDescription) {
        List<WeightedKeyword> top = jobKeywordExtractor.extractTopKeywords(jobDescription, DEFAULT_LIMIT);
        String resumeText = latexPlainTextExtractor.extract(resumeLatex);

        if (top.isEmpty()) {
            return new AtsInsights(
                    0,
                    List.of(),
                    List.of(),
                    buildHeatmap(top, bulletLatex),
                    List.of("ATS-style estimate is heuristic and based on keyword coverage only.")
            );
        }

        double totalWeight = 0.0d;
        double presentWeight = 0.0d;
        List<AtsKeyword> all = new ArrayList<>();

        for (WeightedKeyword k : top) {
            totalWeight += Math.max(0.0d, k.weight());
            boolean present = keywordMatcher.containsKeyword(resumeText, k.keyword());
            if (present) {
                presentWeight += Math.max(0.0d, k.weight());
            }
            List<String> evidence = present ? findEvidenceBullets(bulletLatex, k.keyword()) : List.of();
            all.add(new AtsKeyword(k.keyword(), present, evidence, List.of()));
        }

        int score = totalWeight <= 0.0d ? 0 : (int) Math.round(100.0d * (presentWeight / totalWeight));

        int mustCount = Math.min(MUST_HAVE_COUNT, all.size());
        int niceCount = Math.min(NICE_TO_HAVE_COUNT, Math.max(0, all.size() - mustCount));
        List<AtsKeyword> mustHave = all.subList(0, mustCount);
        List<AtsKeyword> niceToHave = all.subList(mustCount, mustCount + niceCount);

        List<String> notes = List.of(
                "ATS-style estimate is heuristic and based on keyword coverage only.",
                "Click a keyword chip to filter bullets in Review."
        );

        return new AtsInsights(score, mustHave, niceToHave, buildHeatmap(top, bulletLatex), notes);
    }

    private List<BulletHeat> buildHeatmap(List<WeightedKeyword> topKeywords, List<String> bulletLatex) {
        if (bulletLatex == null || bulletLatex.isEmpty()) {
            return List.of();
        }

        List<BulletHeat> heat = new ArrayList<>(bulletLatex.size());
        for (int i = 0; i < bulletLatex.size(); i++) {
            String bulletText = latexPlainTextExtractor.extract(bulletLatex.get(i));
            List<String> matched = new ArrayList<>();
            for (WeightedKeyword k : topKeywords) {
                if (keywordMatcher.containsKeyword(bulletText, k.keyword())) {
                    matched.add(k.keyword());
                }
            }

            // Keep this compact for the UI.
            if (matched.size() > 12) {
                matched = matched.subList(0, 12);
            }

            heat.add(new BulletHeat("B" + (i + 1), matched));
        }
        return heat;
    }

    private List<String> findEvidenceBullets(List<String> bulletLatex, String keyword) {
        if (bulletLatex == null || bulletLatex.isEmpty()) {
            return List.of();
        }

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < bulletLatex.size(); i++) {
            String bulletText = latexPlainTextExtractor.extract(bulletLatex.get(i));
            if (keywordMatcher.containsKeyword(bulletText, keyword)) {
                ids.add("B" + (i + 1));
                if (ids.size() >= 4) {
                    break;
                }
            }
        }
        return ids.isEmpty() ? Collections.emptyList() : ids;
    }
}

