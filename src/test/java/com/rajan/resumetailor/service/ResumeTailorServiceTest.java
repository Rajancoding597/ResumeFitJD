package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rajan.resumetailor.dto.ResumeTailorRequest;
import com.rajan.resumetailor.dto.ResumeTailorResponse;
import com.rajan.resumetailor.dto.RegenerateBulletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeTailorServiceTest {

    private final LatexResumeService latexResumeService = new LatexResumeService();
    private final JobKeywordExtractor jobKeywordExtractor = new JobKeywordExtractor();
    private final LatexPlainTextExtractor latexPlainTextExtractor = new LatexPlainTextExtractor();
    private final KeywordMatcher keywordMatcher = new KeywordMatcher();
    private final KeywordInsightsService keywordInsightsService =
            new KeywordInsightsService(jobKeywordExtractor, latexPlainTextExtractor, keywordMatcher);
    private final ResumeRewriteSafety resumeRewriteSafety = new ResumeRewriteSafety();
    private final LatexEscaper latexEscaper = new LatexEscaper();
    private final com.rajan.resumetailor.config.AiPromptLimitsProperties limits = new com.rajan.resumetailor.config.AiPromptLimitsProperties();
    private final TextLimiter textLimiter = new TextLimiter();

    @Test
    void keepsOriginalBulletWhenRewriteAddsUnsupportedNumbers() {
        ResumeRewriterClient fakeClient = request -> List.of(
                new RewriteSuggestion("Improved system performance by 65% across three teams.", "Added impact and stronger wording.")
        );

        LatexInsightsClient latexInsightsClient = req -> new LatexInsightsResult(
                new com.rajan.resumetailor.dto.AtsInsights(50, List.of(), List.of(), List.of(), List.of("AI insights")),
                List.of("Suggestion 1"),
                List.of("Insights warning")
        );
        ResumeTailorService service = new ResumeTailorService(
                latexResumeService,
                fakeClient,
                keywordInsightsService,
                latexInsightsClient,
                resumeRewriteSafety,
                latexEscaper,
                latexPlainTextExtractor,
                limits,
                textLimiter
        );
        ResumeTailorResponse response = service.tailorResume(new ResumeTailorRequest(
                "\\begin{itemize}\n\\item Improved system performance for internal users.\n\\end{itemize}",
                "Looking for a backend engineer with performance optimization experience."
        ));

        assertEquals("Improved system performance for internal users.", response.changes().get(0).revisedBullet());
        assertEquals("Improved system performance by 65% across three teams.", response.changes().get(0).rejectedDraft());
        assertEquals(true, response.changes().get(0).safetyRejected());
        assertTrue(response.warnings().stream().anyMatch(w -> w.contains("Bullet 1")));
        assertTrue(response.warnings().stream().anyMatch(w -> w.contains("Insights warning")));
        assertEquals(50, response.atsInsights().overallScore());
        assertTrue(response.generalSuggestions().contains("Suggestion 1"));
    }

    @Test
    void returnsUpdatedLatexWhenRewritePassesValidation() {
        ResumeRewriterClient fakeClient = request -> List.of(
                new RewriteSuggestion(
                        "Built internal dashboards that highlighted delivery metrics for engineering stakeholders.",
                        "Emphasizes analytics and stakeholder visibility from the original work."
                )
        );

        LatexInsightsClient latexInsightsClient = req -> new LatexInsightsResult(
                new com.rajan.resumetailor.dto.AtsInsights(72, List.of(), List.of(), List.of(), List.of("AI insights")),
                List.of("Add impact"),
                List.of()
        );
        ResumeTailorService service = new ResumeTailorService(
                latexResumeService,
                fakeClient,
                keywordInsightsService,
                latexInsightsClient,
                resumeRewriteSafety,
                latexEscaper,
                latexPlainTextExtractor,
                limits,
                textLimiter
        );
        ResumeTailorResponse response = service.tailorResume(new ResumeTailorRequest(
                "\\begin{itemize}\n\\item Built internal dashboards for delivery metrics.\n\\end{itemize}",
                "Need someone who can communicate operational insights to engineering teams."
        ));

        assertTrue(response.updatedLatex().contains("Built internal dashboards that highlighted delivery metrics for engineering stakeholders."));
        assertEquals(0, response.warnings().size());
        assertEquals(false, response.changes().get(0).safetyRejected());
        assertEquals(null, response.changes().get(0).rejectedDraft());
        assertEquals(72, response.atsInsights().overallScore());
    }

    @Test
    void escapesLatexSensitiveCharactersInAcceptedRewrite() {
        ResumeRewriterClient fakeClient = request -> List.of(
                new RewriteSuggestion(
                        "Created Tableau dashboards for engagement analytics, facilitating 30% faster data-driven decision-making for business teams.",
                        "Keeps the metric while improving alignment."
                )
        );

        LatexInsightsClient latexInsightsClient = req -> new LatexInsightsResult(
                new com.rajan.resumetailor.dto.AtsInsights(60, List.of(), List.of(), List.of(), List.of("AI insights")),
                List.of(),
                List.of()
        );
        ResumeTailorService service = new ResumeTailorService(
                latexResumeService,
                fakeClient,
                keywordInsightsService,
                latexInsightsClient,
                resumeRewriteSafety,
                latexEscaper,
                latexPlainTextExtractor,
                limits,
                textLimiter
        );
        ResumeTailorResponse response = service.tailorResume(new ResumeTailorRequest(
                "\\begin{itemize}\n\\item Created Tableau dashboards for engagement analytics, 30\\% faster data-driven decisions for business teams.\n\\end{itemize}",
                "Need analytics and reporting experience."
        ));

        assertTrue(response.updatedLatex().contains("facilitating 30\\% faster data-driven decision-making for business teams."));
    }

    @Test
    void regenerateEscapesLatexSensitiveCharactersLikeTailorFlow() {
        ResumeRewriterClient fakeClient = request -> List.of(
                new RewriteSuggestion(
                        "Created dashboards that enabled 30% faster reporting for business teams.",
                        "Keeps the metric while improving readability."
                )
        );

        LatexInsightsClient latexInsightsClient = req -> new LatexInsightsResult(null, List.of(), List.of());
        ResumeTailorService service = new ResumeTailorService(
                latexResumeService,
                fakeClient,
                keywordInsightsService,
                latexInsightsClient,
                resumeRewriteSafety,
                latexEscaper,
                latexPlainTextExtractor,
                limits,
                textLimiter
        );

        var response = service.regenerateBullet(new RegenerateBulletRequest(
                "\\begin{itemize}\n\\item Created dashboards enabling 30\\% faster reporting.\n\\end{itemize}",
                "Need analytics and reporting experience.",
                0,
                "Created dashboards enabling 30\\% faster reporting."
        ));

        assertEquals("Created dashboards that enabled 30\\% faster reporting for business teams.", response.revisedBullet());
        assertEquals(false, response.safetyRejected());
        assertEquals(null, response.rejectedDraft());
    }
}
