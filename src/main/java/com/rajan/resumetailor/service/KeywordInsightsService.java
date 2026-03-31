package com.rajan.resumetailor.service;

import com.rajan.resumetailor.dto.KeywordInsights;
import com.rajan.resumetailor.dto.KeywordScore;
import com.rajan.resumetailor.service.JobKeywordExtractor.WeightedKeyword;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KeywordInsightsService {

    private static final int DEFAULT_LIMIT = 30;

    private final JobKeywordExtractor jobKeywordExtractor;
    private final LatexPlainTextExtractor latexPlainTextExtractor;
    private final KeywordMatcher keywordMatcher;

    public KeywordInsightsService(
            JobKeywordExtractor jobKeywordExtractor,
            LatexPlainTextExtractor latexPlainTextExtractor,
            KeywordMatcher keywordMatcher
    ) {
        this.jobKeywordExtractor = jobKeywordExtractor;
        this.latexPlainTextExtractor = latexPlainTextExtractor;
        this.keywordMatcher = keywordMatcher;
    }

    public KeywordInsights build(String resumeLatex, String jobDescription) {
        List<WeightedKeyword> top = jobKeywordExtractor.extractTopKeywords(jobDescription, DEFAULT_LIMIT);
        String resumeText = latexPlainTextExtractor.extract(resumeLatex);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<KeywordScore> scored = new ArrayList<>();

        for (WeightedKeyword keyword : top) {
            boolean inResume = keywordMatcher.containsKeyword(resumeText, keyword.keyword());
            scored.add(new KeywordScore(keyword.keyword(), keyword.weight(), inResume));
            if (inResume) {
                matched.add(keyword.keyword());
            } else {
                missing.add(keyword.keyword());
            }
        }

        int coveragePercent = top.isEmpty()
                ? 0
                : (int) Math.round(100.0d * ((double) matched.size() / (double) top.size()));

        List<String> notes = List.of(
                "These are keyword hints, not instructions to invent skills.",
                "Only add a keyword if you can support it with real experience in your resume."
        );

        return new KeywordInsights(coveragePercent, matched, missing, scored, notes);
    }
}
