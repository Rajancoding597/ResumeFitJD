package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeywordInsightsServiceTest {

    @Test
    void computesDeterministicCoverage() {
        JobKeywordExtractor jobKeywordExtractor = new JobKeywordExtractor();
        LatexPlainTextExtractor latexPlainTextExtractor = new LatexPlainTextExtractor();
        KeywordInsightsService service = new KeywordInsightsService(jobKeywordExtractor, latexPlainTextExtractor, new KeywordMatcher());

        String resumeLatex = """
                \\begin{itemize}
                \\item Built Java services and deployed on AWS.
                \\item Used Kafka for event streaming.
                \\end{itemize}
                """;

        String jd = "Java AWS Kubernetes Kafka";

        var insights = service.build(resumeLatex, jd);
        assertTrue(insights.matchedKeywords().contains("java"));
        assertTrue(insights.matchedKeywords().contains("aws"));
        assertTrue(insights.matchedKeywords().contains("kafka"));
        assertTrue(insights.missingKeywords().contains("kubernetes"));
        assertEquals(75, insights.coveragePercent());
    }
}
