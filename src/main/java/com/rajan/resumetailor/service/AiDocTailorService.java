package com.rajan.resumetailor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajan.resumetailor.dto.AiDocTailorResponse;
import com.rajan.resumetailor.dto.AtsInsights;
import com.rajan.resumetailor.dto.ExtractedBullet;
import com.rajan.resumetailor.dto.ExtractedResume;
import com.rajan.resumetailor.dto.KeywordInsights;
import com.rajan.resumetailor.dto.ResumeChange;
import com.rajan.resumetailor.service.GeminiAiDocTailorClient.AiDocTailorPayload;
import com.rajan.resumetailor.service.GeminiAiDocTailorClient.TailoredBullet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiDocTailorService {

    private final ObjectMapper objectMapper;
    private final GeminiAiDocTailorClient geminiAiDocTailorClient;
    private final ResumeRewriteSafety resumeRewriteSafety;
    private final KeywordInsightsService keywordInsightsService;
    private final AiFailureClassifier failureClassifier;
    private final com.rajan.resumetailor.config.AiPromptLimitsProperties limits;
    private final TextLimiter textLimiter;

    public AiDocTailorService(
            ObjectMapper objectMapper,
            GeminiAiDocTailorClient geminiAiDocTailorClient,
            ResumeRewriteSafety resumeRewriteSafety,
            KeywordInsightsService keywordInsightsService,
            AiFailureClassifier failureClassifier,
            com.rajan.resumetailor.config.AiPromptLimitsProperties limits,
            TextLimiter textLimiter
    ) {
        this.objectMapper = objectMapper;
        this.geminiAiDocTailorClient = geminiAiDocTailorClient;
        this.resumeRewriteSafety = resumeRewriteSafety;
        this.keywordInsightsService = keywordInsightsService;
        this.failureClassifier = failureClassifier;
        this.limits = limits;
        this.textLimiter = textLimiter;
    }

    public AiDocTailorResponse tailorFromAiDoc(MultipartFile resumeFile, String jobDescription, String optionsJson) {
        if (resumeFile == null || resumeFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resumeFile is required.");
        }
        if (jobDescription == null || jobDescription.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobDescription is required.");
        }

        String contentType = resolveContentType(resumeFile);
        if (!isSupportedMimeType(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported resumeFile type. Upload a PDF or an image (PNG/JPG/WEBP).");
        }

        long maxBytes = contentType.equals("application/pdf") ? 10L * 1024L * 1024L : 5L * 1024L * 1024L;
        if (resumeFile.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File too large for this demo.");
        }

        AiDocOptions options = parseOptions(optionsJson);
        String jdLimited = textLimiter.limitNoEllipsis(
                jobDescription,
                Math.max(1000, limits.getMaxJobDescriptionChars()),
                "Job description"
        );
        AiDocTailorPayload payload;
        try {
            payload = geminiAiDocTailorClient.tailorFromDocument(resumeFile, contentType, jdLimited, options);
        } catch (Exception exception) {
            if (failureClassifier.isQuotaExhausted(exception)) {
                List<String> warnings = new ArrayList<>();
                warnings.add("AI provider quota was exhausted; document analysis is temporarily unavailable.");
                warnings.add("Tip: switch to LaTeX mode and paste your resume source if available.");

                KeywordInsights keywordInsights = keywordInsightsService.build("", jdLimited);
                return new AiDocTailorResponse(
                        new ExtractedResume(List.of(), null),
                        List.of(),
                        keywordInsights,
                        null,
                        List.of("Try again later when the provider quota resets, or use LaTeX mode."),
                        warnings
                );
            }
            throw exception;
        }

        List<String> warnings = new ArrayList<>();
        if (payload.warnings() != null) {
            warnings.addAll(payload.warnings());
        }

        ExtractedResume extractedResume = sanitizeExtracted(payload.extracted());
        List<ResumeChange> changes = buildChangesWithSafety(extractedResume, payload.tailored(), warnings);
        KeywordInsights keywordInsights = payload.keywordInsights();
        if (keywordInsights == null) {
            String resumeText = joinBullets(extractedResume.bullets());
            keywordInsights = keywordInsightsService.build(resumeText, jdLimited);
        }

        AtsInsights atsInsights = payload.atsInsights();

        List<String> suggestions = payload.generalSuggestions() == null ? List.of() : payload.generalSuggestions();
        if (suggestions.size() > 12) {
            suggestions = suggestions.subList(0, 12);
        }

        return new AiDocTailorResponse(extractedResume, changes, keywordInsights, atsInsights, suggestions, warnings);
    }

    private ExtractedResume sanitizeExtracted(ExtractedResume extracted) {
        if (extracted == null || extracted.bullets() == null) {
            return new ExtractedResume(List.of(), null);
        }
        String preview = extracted.rawTextPreview();
        if (preview != null && preview.length() > 1200) {
            preview = preview.substring(0, 1200);
        }
        return new ExtractedResume(extracted.bullets(), preview);
    }

    private List<ResumeChange> buildChangesWithSafety(ExtractedResume extractedResume, List<TailoredBullet> tailoredBullets, List<String> warnings) {
        Map<String, TailoredBullet> byId = new HashMap<>();
        if (tailoredBullets != null) {
            for (TailoredBullet t : tailoredBullets) {
                if (t != null && t.id() != null) {
                    byId.put(t.id(), t);
                }
            }
        }

        List<ResumeChange> changes = new ArrayList<>();
        List<ExtractedBullet> extracted = extractedResume.bullets() == null ? List.of() : extractedResume.bullets();
        for (int i = 0; i < extracted.size(); i++) {
            ExtractedBullet bullet = extracted.get(i);
            String original = resumeRewriteSafety.normalizeInlineWhitespace(bullet.originalBullet());
            TailoredBullet tailored = byId.get(bullet.id());

            if (tailored == null) {
                warnings.add("Bullet " + (i + 1) + " had no AI rewrite; original bullet was kept.");
                changes.add(new ResumeChange(original, original, "Original bullet kept because no rewrite was returned.", true, null));
                continue;
            }

            String draft = resumeRewriteSafety.normalizeInlineWhitespace(tailored.revisedBullet());
            var outcome = resumeRewriteSafety.validateSuggestion(original, draft);
            boolean rejected = !outcome.accepted();

            String finalBullet = rejected ? original : draft;
            String reason = rejected ? outcome.reason() : resumeRewriteSafety.safeReason(tailored.reason());
            if (rejected) {
                warnings.add("Bullet " + (i + 1) + " was kept as-is because the rewrite failed safety checks.");
            }

            changes.add(new ResumeChange(original, finalBullet, reason, rejected, rejected ? draft : null));
        }

        return changes;
    }

    private String joinBullets(List<ExtractedBullet> bullets) {
        if (bullets == null || bullets.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ExtractedBullet bullet : bullets) {
            if (bullet != null && bullet.originalBullet() != null) {
                builder.append(bullet.originalBullet()).append('\n');
            }
        }
        return builder.toString();
    }

    private AiDocOptions parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return AiDocOptions.defaults();
        }
        try {
            return objectMapper.readValue(optionsJson, AiDocOptions.class);
        } catch (JsonProcessingException ignored) {
            return AiDocOptions.defaults();
        }
    }

    private boolean isSupportedMimeType(String contentType) {
        return "application/pdf".equals(contentType)
                || "image/png".equals(contentType)
                || "image/jpeg".equals(contentType)
                || "image/webp".equals(contentType);
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            if ("image/jpg".equalsIgnoreCase(contentType)) {
                return "image/jpeg";
            }
            return contentType.toLowerCase();
        }

        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".webp")) return "image/webp";
        return "";
    }
}
