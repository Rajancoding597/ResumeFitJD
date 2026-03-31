package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rajan.resumetailor.dto.ApplyBulletsRequest;
import com.rajan.resumetailor.dto.BulletOverride;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplyBulletsTest {

    private ResumeTailorService newService() {
        LatexResumeService latexResumeService = new LatexResumeService();
        ResumeRewriterClient noopClient = request -> List.of();
        JobKeywordExtractor jobKeywordExtractor = new JobKeywordExtractor();
        LatexPlainTextExtractor latexPlainTextExtractor = new LatexPlainTextExtractor();
        KeywordMatcher keywordMatcher = new KeywordMatcher();
        KeywordInsightsService keywordInsightsService = new KeywordInsightsService(jobKeywordExtractor, latexPlainTextExtractor, keywordMatcher);
        LatexInsightsClient latexInsightsClient = req -> new LatexInsightsResult(null, List.of(), List.of());
        return new ResumeTailorService(
                latexResumeService,
                noopClient,
                keywordInsightsService,
                latexInsightsClient,
                new ResumeRewriteSafety(),
                new LatexEscaper(),
                latexPlainTextExtractor,
                new com.rajan.resumetailor.config.AiPromptLimitsProperties(),
                new TextLimiter()
        );
    }

    @Test
    void appliesAcceptedEditsAndKeepsRejectedBullets() {
        ResumeTailorService service = newService();

        String latex = """
                \\begin{itemize}
                \\item First bullet.
                \\item Second bullet.
                \\end{itemize}
                """;

        var response = service.applyBullets(new ApplyBulletsRequest(
                latex,
                List.of(
                        new BulletOverride(0, true, "Edited first bullet."),
                        new BulletOverride(1, false, "Should not apply")
                )
        ));

        assertTrue(response.updatedLatex().contains("Edited first bullet."));
        assertTrue(response.updatedLatex().contains("Second bullet."));
    }

    @Test
    void escapesLatexSensitiveCharactersInAcceptedEdits() {
        ResumeTailorService service = newService();

        String latex = """
                \\begin{itemize}
                \\item First bullet.
                \\end{itemize}
                """;

        var response = service.applyBullets(new ApplyBulletsRequest(
                latex,
                List.of(new BulletOverride(0, true, "Improved throughput by 30% for R&D systems_1 & ops #1, saving $100."))
        ));

        assertTrue(response.updatedLatex().contains("30\\%"));
        assertTrue(response.updatedLatex().contains("R\\&D systems\\_1"));
        assertTrue(response.updatedLatex().contains("\\&"));
        assertTrue(response.updatedLatex().contains("\\#1"));
        assertTrue(response.updatedLatex().contains("\\$100"));
    }
}
