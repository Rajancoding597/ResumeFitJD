package com.rajan.resumetailor.service;

import com.rajan.resumetailor.dto.AtsInsights;
import com.rajan.resumetailor.dto.CoachAction;
import com.rajan.resumetailor.dto.CoachBulletContext;
import com.rajan.resumetailor.dto.CoachChatMessage;
import com.rajan.resumetailor.dto.CoachOptions;
import com.rajan.resumetailor.dto.KeywordInsights;
import com.rajan.resumetailor.dto.KeywordScore;
import com.rajan.resumetailor.dto.ResumeCoachChatRequest;
import com.rajan.resumetailor.dto.ResumeCoachChatResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResumeCoachChatService {

    private static final int DEFAULT_MAX_EDITS = 3;

    private final GeminiResumeCoachClient geminiResumeCoachClient;
    private final ResumeRewriteSafety resumeRewriteSafety;
    private final KeywordInsightsService keywordInsightsService;

    public ResumeCoachChatService(
            GeminiResumeCoachClient geminiResumeCoachClient,
            ResumeRewriteSafety resumeRewriteSafety,
            KeywordInsightsService keywordInsightsService
    ) {
        this.geminiResumeCoachClient = geminiResumeCoachClient;
        this.resumeRewriteSafety = resumeRewriteSafety;
        this.keywordInsightsService = keywordInsightsService;
    }

    public ResumeCoachChatResponse chat(ResumeCoachChatRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required.");
        }

        String mode = normalizeMode(request.mode());
        if (mode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be 'upload' or 'latex'.");
        }
        String jobDescription = request.jobDescription() == null ? "" : request.jobDescription().trim();
        if (jobDescription.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobDescription is required.");
        }

        List<CoachBulletContext> bullets = request.bullets() == null ? List.of() : request.bullets();
        if (bullets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bullets are required. Run Tailor first to load context.");
        }

        CoachOptions options = request.options();
        int maxEdits = DEFAULT_MAX_EDITS;
        if (options != null && options.maxEdits() != null) {
            maxEdits = Math.max(0, Math.min(8, options.maxEdits()));
        }

        KeywordInsights keywordInsights = request.keywordInsights();
        if (keywordInsights == null) {
            String resumeText = joinBulletText(bullets, true);
            keywordInsights = keywordInsightsService.build(resumeText, jobDescription);
        }

        List<String> allowedKeywords = pickAllowedKeywords(keywordInsights);
        AtsInsights atsInsights = request.atsInsights();
        List<CoachChatMessage> messages = request.messages() == null ? List.of() : request.messages();

        String prompt = buildPrompt(mode, jobDescription, request.resumeLatex(), bullets, keywordInsights, atsInsights, allowedKeywords, messages, maxEdits, options == null ? null : options.tone());
        var payload = geminiResumeCoachClient.chat(prompt);

        List<String> warnings = new ArrayList<>();
        if (payload.warnings() != null) warnings.addAll(payload.warnings());

        List<CoachAction> sanitizedActions = sanitizeActions(payload.actions(), bullets, maxEdits, warnings);
        String assistantMessage = payload.assistantMessage() == null ? "" : payload.assistantMessage().trim();
        if (assistantMessage.isBlank()) {
            assistantMessage = "I can help. Tell me what role you're targeting and which bullets feel weakest.";
        }

        return new ResumeCoachChatResponse(assistantMessage, sanitizedActions, warnings);
    }

    private String normalizeMode(String mode) {
        if (mode == null) return "";
        String m = mode.trim().toLowerCase(Locale.ROOT);
        return (m.equals("upload") || m.equals("latex")) ? m : "";
    }

    private String joinBulletText(List<CoachBulletContext> bullets, boolean useCurrent) {
        StringBuilder builder = new StringBuilder();
        for (CoachBulletContext b : bullets) {
            if (b == null) continue;
            String text = useCurrent ? b.current() : b.original();
            if (text == null) continue;
            builder.append(text).append('\n');
        }
        return builder.toString();
    }

    private List<String> pickAllowedKeywords(KeywordInsights keywordInsights) {
        if (keywordInsights == null || keywordInsights.topKeywords() == null) {
            return List.of();
        }
        List<String> allowed = new ArrayList<>();
        for (KeywordScore score : keywordInsights.topKeywords()) {
            if (score == null) continue;
            if (!score.inResume()) continue;
            String kw = score.keyword();
            if (kw == null) continue;
            kw = kw.trim();
            if (kw.isBlank()) continue;
            allowed.add(kw);
            if (allowed.size() >= 10) break;
        }
        return allowed;
    }

    private String buildPrompt(
            String mode,
            String jobDescription,
            String resumeLatex,
            List<CoachBulletContext> bullets,
            KeywordInsights keywordInsights,
            AtsInsights atsInsights,
            List<String> allowedKeywords,
            List<CoachChatMessage> messages,
            int maxEdits,
            String tone
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the Resume Coach for this session.\n");
        prompt.append("Mode: ").append(mode).append('\n');
        if (tone != null && !tone.isBlank()) {
            prompt.append("Tone preference: ").append(tone.trim()).append('\n');
        }
        prompt.append("Max edit actions: ").append(maxEdits).append("\n\n");

        prompt.append("Job description:\n").append(jobDescription.trim()).append("\n\n");

        if (!allowedKeywords.isEmpty()) {
            prompt.append("Allowed keywords (use sparingly; only if truly supported by the bullets; avoid keyword stuffing):\n");
            for (String kw : allowedKeywords) {
                prompt.append("- ").append(kw).append('\n');
            }
            prompt.append('\n');
        }

        if (keywordInsights != null && keywordInsights.missingKeywords() != null && !keywordInsights.missingKeywords().isEmpty()) {
            prompt.append("Missing keyword hints (do not add unless supported by existing experience):\n");
            for (String kw : keywordInsights.missingKeywords().subList(0, Math.min(10, keywordInsights.missingKeywords().size()))) {
                prompt.append("- ").append(kw).append('\n');
            }
            prompt.append('\n');
        }

        if ("latex".equals(mode) && resumeLatex != null && !resumeLatex.isBlank()) {
            prompt.append("Resume source: LaTeX bullets were parsed; do not propose layout changes.\n\n");
        }

        prompt.append("Current bullets (index is 1-based):\n");
        for (int i = 0; i < bullets.size(); i++) {
            CoachBulletContext b = bullets.get(i);
            if (b == null) continue;
            String original = resumeRewriteSafety.normalizeInlineWhitespace(b.original());
            String current = resumeRewriteSafety.normalizeInlineWhitespace(b.current());
            prompt.append("Bullet ").append(i + 1).append(":\n");
            prompt.append("Original: ").append(original).append('\n');
            prompt.append("Current: ").append(current).append('\n');
            prompt.append("Flags: accepted=").append(Boolean.TRUE.equals(b.accepted()))
                    .append(", safetyRejected=").append(Boolean.TRUE.equals(b.safetyRejected()))
                    .append("\n\n");
        }

        if ("upload".equals(mode) && atsInsights != null) {
            prompt.append("ATS insights summary (heuristic): overallScore=").append(atsInsights.overallScore()).append('\n');
            prompt.append("Use this only to guide wording; do not invent new skills.\n\n");
        }

        prompt.append("Conversation (most recent last):\n");
        List<CoachChatMessage> trimmed = trimConversation(messages, 12);
        for (CoachChatMessage msg : trimmed) {
            if (msg == null) continue;
            String role = msg.role() == null ? "" : msg.role().trim().toLowerCase(Locale.ROOT);
            if (!role.equals("user") && !role.equals("assistant")) continue;
            String content = msg.content() == null ? "" : msg.content().trim();
            if (content.isBlank()) continue;
            prompt.append(role.equals("user") ? "User: " : "Assistant: ");
            prompt.append(content).append('\n');
        }

        prompt.append("\nOutput instructions:\n");
        prompt.append("- Always provide assistantMessage.\n");
        prompt.append("- Provide 0..").append(maxEdits).append(" actions.\n");
        prompt.append("- Use action type edit_bullet only when you are confident the edit is supported by the original bullet.\n");
        prompt.append("- Otherwise, use ask_user to request missing details.\n");

        return prompt.toString();
    }

    private List<CoachChatMessage> trimConversation(List<CoachChatMessage> messages, int max) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        if (messages.size() <= max) return messages;
        return messages.subList(messages.size() - max, messages.size());
    }

    private List<CoachAction> sanitizeActions(List<CoachAction> actions, List<CoachBulletContext> bullets, int maxEdits, List<String> warnings) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }

        List<CoachAction> sanitized = new ArrayList<>();
        int editCount = 0;
        for (CoachAction action : actions) {
            if (action == null) continue;
            String type = action.type() == null ? "" : action.type().trim().toLowerCase(Locale.ROOT);
            String reason = resumeRewriteSafety.safeReason(action.reason());
            if (reason.isBlank()) reason = "Suggested by coach.";

            if (type.equals("edit_bullet")) {
                if (editCount >= maxEdits) continue;
                Integer idx = action.bulletIndex();
                if (idx == null || idx < 0 || idx >= bullets.size()) {
                    warnings.add("Coach suggested an edit for an unknown bullet; it was ignored.");
                    continue;
                }
                CoachBulletContext bullet = bullets.get(idx);
                String original = resumeRewriteSafety.normalizeInlineWhitespace(bullet.original());

                String draft = resumeRewriteSafety.normalizeInlineWhitespace(action.suggestedText());
                if (draft.isBlank()) {
                    warnings.add("Coach suggested a blank edit; it was ignored.");
                    continue;
                }

                var outcome = resumeRewriteSafety.validateSuggestion(original, draft);
                boolean rejected = !outcome.accepted();
                String finalText = rejected ? original : draft;

                sanitized.add(new CoachAction(
                        "edit_bullet",
                        idx,
                        finalText,
                        rejected ? outcome.reason() : reason,
                        null,
                        null,
                        rejected,
                        rejected ? draft : null
                ));
                editCount++;
                continue;
            }

            if (type.equals("pin_keywords")) {
                List<String> kws = action.keywords() == null ? List.of() : action.keywords();
                List<String> cleaned = new ArrayList<>();
                for (String kw : kws) {
                    if (kw == null) continue;
                    String v = kw.trim();
                    if (v.isBlank()) continue;
                    cleaned.add(v);
                    if (cleaned.size() >= 12) break;
                }
                if (cleaned.isEmpty()) continue;
                sanitized.add(new CoachAction("pin_keywords", null, null, reason, cleaned, null, null, null));
                continue;
            }

            if (type.equals("ask_user")) {
                String question = action.question() == null ? "" : action.question().trim();
                if (question.isBlank()) continue;
                sanitized.add(new CoachAction("ask_user", null, null, reason, null, question, null, null));
                continue;
            }
        }

        return sanitized.isEmpty() ? Collections.emptyList() : sanitized;
    }
}

