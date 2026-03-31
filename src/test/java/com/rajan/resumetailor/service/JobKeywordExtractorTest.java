package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JobKeywordExtractorTest {

    private final JobKeywordExtractor extractor = new JobKeywordExtractor();

    @Test
    void retainsCommonTechnicalTokens() {
        String jd = """
                Looking for a backend engineer with C++, C#, and .NET experience.
                Must know AWS, GCP, Kafka, OAuth2, CI/CD, and Kubernetes.
                """;

        var keywords = extractor.extractTopKeywords(jd, 50);
        String joined = keywords.stream().map(JobKeywordExtractor.WeightedKeyword::keyword).reduce("", (a, b) -> a + " " + b);

        assertTrue(joined.contains("c++"));
        assertTrue(joined.contains("c#"));
        assertTrue(joined.contains(".net"));
        assertTrue(joined.contains("aws"));
        assertTrue(joined.contains("gcp"));
        assertTrue(joined.contains("kafka"));
        assertTrue(joined.contains("oauth2"));
        assertTrue(joined.contains("ci/cd"));
        assertTrue(joined.contains("kubernetes"));
    }
}

