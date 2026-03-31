package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResumeRewriteSafetyTest {

    private final ResumeRewriteSafety safety = new ResumeRewriteSafety();

    @Test
    void acceptsNumericEquivalenceForMultiplierSymbols() {
        var outcome = safety.validateSuggestion(
                "Improved API latency by 1.5x for internal users.",
                "Improved API latency by 1.5 × for internal users."
        );
        assertTrue(outcome.accepted());
    }

    @Test
    void acceptsNumericEquivalenceForLatexPercent() {
        var outcome = safety.validateSuggestion(
                "Reduced error rate by 30\\% through better alerting.",
                "Reduced error rate by 30% through better alerting."
        );
        assertTrue(outcome.accepted());
    }

    @Test
    void acceptsNumericEquivalenceForThousandsSeparators() {
        var outcome = safety.validateSuggestion(
                "Processed 1,000 records daily with batch pipelines.",
                "Processed 1000 records daily with batch pipelines."
        );
        assertTrue(outcome.accepted());
    }

    @Test
    void rejectsUnsupportedNewNumbers() {
        var outcome = safety.validateSuggestion(
                "Improved system performance for internal users.",
                "Improved system performance by 65% for internal users."
        );
        assertFalse(outcome.accepted());
        assertTrue(outcome.reason().toLowerCase().contains("numeric"));
    }
}

