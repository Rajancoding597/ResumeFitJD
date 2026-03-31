package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajan.resumetailor.config.GeminiProperties;
import com.rajan.resumetailor.dto.CoachAction;
import com.rajan.resumetailor.dto.CoachBulletContext;
import com.rajan.resumetailor.dto.CoachChatMessage;
import com.rajan.resumetailor.dto.ResumeCoachChatRequest;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeCoachChatServiceTest {

    private final KeywordInsightsService keywordInsightsService =
            new KeywordInsightsService(new JobKeywordExtractor(), new LatexPlainTextExtractor(), new KeywordMatcher());
    private final ResumeRewriteSafety resumeRewriteSafety = new ResumeRewriteSafety();
    private final AiFailureClassifier failureClassifier = new AiFailureClassifier();
    private final com.rajan.resumetailor.config.AiPromptLimitsProperties limits = new com.rajan.resumetailor.config.AiPromptLimitsProperties();
    private final TextLimiter textLimiter = new TextLimiter();
    private final ChatContextCompactor chatContextCompactor = new ChatContextCompactor(limits, textLimiter);

    @Test
    void rejectsCoachEditThatAddsNewNumericClaim() {
        class FakeClient extends GeminiResumeCoachClient {
            FakeClient() {
                super(
                        new GeminiProperties(),
                        new ObjectMapper(),
                        HttpClient.newHttpClient(),
                        new GeminiApiSupport(),
                        new AiCallExecutor(new com.rajan.resumetailor.config.AiExecutionProperties(), new AiFailureClassifier()),
                        new GeminiApiKeyResolver(new GeminiProperties(), false)
                );
            }

            @Override
            public CoachChatPayload chat(String prompt) {
                return new CoachChatPayload(
                        "Try this.",
                        List.of(new CoachAction("edit_bullet", 0, "Improved performance by 65% for internal users.", "Adds impact.", null, null, null, null)),
                        List.of()
                );
            }
        }

        ResumeCoachChatService service = new ResumeCoachChatService(new FakeClient(), resumeRewriteSafety, keywordInsightsService, failureClassifier, limits, textLimiter, chatContextCompactor);
        var response = service.chat(new ResumeCoachChatRequest(
                "upload",
                "Need performance optimization.",
                null,
                List.of(new CoachBulletContext(0, null, "Improved performance for internal users.", "Improved performance for internal users.", true, false)),
                List.of(new CoachChatMessage("user", "Make it stronger.")),
                null,
                null,
                null
        ));

        assertEquals(1, response.actions().size());
        var action = response.actions().get(0);
        assertEquals(true, action.safetyRejected());
        assertEquals("Improved performance for internal users.", action.suggestedText());
        assertEquals("Improved performance by 65% for internal users.", action.rejectedDraft());
    }

    @Test
    void ignoresOutOfRangeBulletEditsAndAddsWarning() {
        class FakeClient extends GeminiResumeCoachClient {
            FakeClient() {
                super(
                        new GeminiProperties(),
                        new ObjectMapper(),
                        HttpClient.newHttpClient(),
                        new GeminiApiSupport(),
                        new AiCallExecutor(new com.rajan.resumetailor.config.AiExecutionProperties(), new AiFailureClassifier()),
                        new GeminiApiKeyResolver(new GeminiProperties(), false)
                );
            }

            @Override
            public CoachChatPayload chat(String prompt) {
                return new CoachChatPayload(
                        "OK",
                        List.of(new CoachAction("edit_bullet", 5, "Text", "Reason", null, null, null, null)),
                        List.of()
                );
            }
        }

        ResumeCoachChatService service = new ResumeCoachChatService(new FakeClient(), resumeRewriteSafety, keywordInsightsService, failureClassifier, limits, textLimiter, chatContextCompactor);
        var response = service.chat(new ResumeCoachChatRequest(
                "latex",
                "Need Java.",
                "\\\\begin{itemize}\\\\item Built services.\\\\end{itemize}",
                List.of(new CoachBulletContext(0, null, "Built services.", "Built services.", true, false)),
                List.of(new CoachChatMessage("user", "Help.")),
                null,
                null,
                null
        ));

        assertTrue(response.actions().isEmpty());
        assertTrue(response.warnings().stream().anyMatch((w) -> w.toLowerCase().contains("unknown bullet")));
    }
}
