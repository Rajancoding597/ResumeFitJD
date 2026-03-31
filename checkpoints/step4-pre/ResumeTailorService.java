package com.rajan.resumetailor.service;

import com.rajan.resumetailor.dto.ResumeChange;
import com.rajan.resumetailor.dto.RegenerateBulletRequest;
import com.rajan.resumetailor.dto.RegenerateBulletResponse;
import com.rajan.resumetailor.dto.ResumeTailorRequest;
import com.rajan.resumetailor.dto.ResumeTailorResponse;
import com.rajan.resumetailor.dto.ApplyBulletsRequest;
import com.rajan.resumetailor.dto.ApplyBulletsResponse;
import com.rajan.resumetailor.dto.BulletOverride;
import com.rajan.resumetailor.dto.KeywordScore;
import com.rajan.resumetailor.service.LatexResumeService.BulletSegment;
import com.rajan.resumetailor.service.LatexResumeService.ParsedResume;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResumeTailorService {

    private final LatexResumeService latexResumeService;
    private final ResumeRewriterClient resumeRewriterClient;
    private final KeywordInsightsService keywordInsightsService;
    private final ResumeRewriteSafety resumeRewriteSafety;
    private final LatexEscaper latexEscaper;

    public ResumeTailorService(
            LatexResumeService latexResumeService,
            ResumeRewriterClient resumeRewriterClient,
            KeywordInsightsService keywordInsightsService,
            ResumeRewriteSafety resumeRewriteSafety,
            LatexEscaper latexEscaper
    ) {
        this.latexResumeService = latexResumeService;
        this.resumeRewriterClient = resumeRewriterClient;
        this.keywordInsightsService = keywordInsightsService;
        this.resumeRewriteSafety = resumeRewriteSafety;
        this.latexEscaper = latexEscaper;
    }

    public ResumeTailorResponse tailorResume(ResumeTailorRequest request) {
        AiCallWarnings.clear();
        var keywordInsights = keywordInsightsService.build(request.resumeLatex(), request.jobDescription());
        List<String> allowedKeywords = deriveAllowedKeywords(keywordInsights.topKeywords());

        ParsedResume parsedResume = latexResumeService.parse(request.resumeLatex());
        if (parsedResume.bullets().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No LaTeX bullets were found. Use \\item blocks or \\resumeItem{...} entries."
            );
        }

        List<String> originalBullets = parsedResume.bullets().stream()
                .map(BulletSegment::originalBullet)
                .toList();

        List<String> warnings = new ArrayList<>();
        List<RewriteSuggestion> suggestions = resumeRewriterClient.rewriteBullets(new RewriteBulletsRequest(
                originalBullets,
                request.jobDescription(),
                allowedKeywords
        ));
        warnings.addAll(AiCallWarnings.drain());
        if (suggestions.size() != originalBullets.size()) {
            warnings.add("The AI response did not return the expected number of bullets, so the original resume was preserved.");
            List<ResumeChange> unchanged = originalBullets.stream()
                    .map(bullet -> new ResumeChange(
                            bullet,
                            bullet,
                            "Original bullet kept because the AI response was incomplete.",
                            true,
                            null
                    ))
                    .toList();
            return new ResumeTailorResponse(parsedResume.originalLatex(), unchanged, warnings, keywordInsights);
        }

        List<String> revisedBullets = new ArrayList<>();
        List<ResumeChange> changes = new ArrayList<>();

        for (int index = 0; index < originalBullets.size(); index++) {
            String original = originalBullets.get(index);
            RewriteSuggestion suggestion = suggestions.get(index);
            ResumeRewriteSafety.ValidationOutcome validation = resumeRewriteSafety.validateSuggestion(original, suggestion.revisedBullet());
            String normalizedDraft = resumeRewriteSafety.normalizeInlineWhitespace(suggestion.revisedBullet());
            String latexSafeDraft = latexEscaper.escapeBulletText(normalizedDraft);

            String finalBullet = validation.accepted() ? latexSafeDraft : original;
            String finalReason = validation.accepted() ? resumeRewriteSafety.safeReason(suggestion.reason()) : validation.reason();

            if (!validation.accepted()) {
                warnings.add("Bullet " + (index + 1) + " was kept as-is because the rewrite failed safety checks.");
            }

            revisedBullets.add(finalBullet);
            changes.add(new ResumeChange(
                    original,
                    finalBullet,
                    finalReason,
                    !validation.accepted(),
                    validation.accepted() ? null : normalizedDraft
            ));
        }

        String updatedLatex = latexResumeService.merge(parsedResume, revisedBullets);
        return new ResumeTailorResponse(updatedLatex, changes, warnings, keywordInsights);
    }

    public ApplyBulletsResponse applyBullets(ApplyBulletsRequest request) {
        ParsedResume parsedResume = latexResumeService.parse(request.resumeLatex());
        if (parsedResume.bullets().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No LaTeX bullets were found. Use \\item blocks or \\resumeItem{...} entries."
            );
        }

        Map<Integer, BulletOverride> overrideByIndex = new HashMap<>();
        for (BulletOverride override : request.overrides()) {
            if (override == null || override.index() == null) {
                continue;
            }
            overrideByIndex.put(override.index(), override);
        }

        List<String> warnings = new ArrayList<>();
        List<String> finalBullets = new ArrayList<>(parsedResume.bullets().size());

        for (int i = 0; i < parsedResume.bullets().size(); i++) {
            String original = parsedResume.bullets().get(i).originalBullet();
            BulletOverride override = overrideByIndex.get(i);

            if (override == null || override.accepted() == null || !override.accepted()) {
                finalBullets.add(original);
                continue;
            }

            String revised = resumeRewriteSafety.normalizeInlineWhitespace(override.revisedBullet());
            if (revised.isBlank()) {
                warnings.add("Bullet " + (i + 1) + " was accepted but blank; original bullet was kept.");
                finalBullets.add(original);
                continue;
            }

            finalBullets.add(latexEscaper.escapeBulletText(revised));
        }

        String updatedLatex = latexResumeService.merge(parsedResume, finalBullets);
        return new ApplyBulletsResponse(updatedLatex, warnings);
    }

    public RegenerateBulletResponse regenerateBullet(RegenerateBulletRequest request) {
        AiCallWarnings.clear();
        var keywordInsights = keywordInsightsService.build(request.resumeLatex(), request.jobDescription());
        List<String> allowedKeywords = deriveAllowedKeywords(keywordInsights.topKeywords());

        ParsedResume parsedResume = latexResumeService.parse(request.resumeLatex());
        if (parsedResume.bullets().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No LaTeX bullets were found. Use \\item blocks or \\resumeItem{...} entries."
            );
        }

        int index = request.bulletIndex();
        if (index < 0 || index >= parsedResume.bullets().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bulletIndex is out of range for this resume.");
        }

        String originalFromLatex = parsedResume.bullets().get(index).originalBullet();

        // Prefer the client's current bullet text for regeneration context, but still validate against the original.
        String current = resumeRewriteSafety.normalizeInlineWhitespace(request.currentBulletText());
        if (current.isBlank()) {
            current = originalFromLatex;
        }

        List<RewriteSuggestion> suggestions = resumeRewriterClient.rewriteBullets(new RewriteBulletsRequest(
                List.of(current),
                request.jobDescription(),
                allowedKeywords
        ));
        // Drain any provider-level warnings even when we return a single-bullet response.
        List<String> providerWarnings = AiCallWarnings.drain();
        if (!providerWarnings.isEmpty()) {
            // Surface the first provider warning as the reason if we have to keep the original.
            // If we accept a rewrite, the per-bullet "reason" already explains the change.
        }
        if (suggestions.isEmpty()) {
            return new RegenerateBulletResponse(
                    index,
                    originalFromLatex,
                    "Original bullet kept because the AI did not return a rewrite.",
                    true,
                    null
            );
        }

        RewriteSuggestion suggestion = suggestions.get(0);
        ResumeRewriteSafety.ValidationOutcome validation = resumeRewriteSafety.validateSuggestion(originalFromLatex, suggestion.revisedBullet());

        String normalizedDraft = resumeRewriteSafety.normalizeInlineWhitespace(suggestion.revisedBullet());
        String latexSafeDraft = latexEscaper.escapeBulletText(normalizedDraft);
        String finalBullet = validation.accepted() ? latexSafeDraft : originalFromLatex;
        String finalReason = validation.accepted() ? resumeRewriteSafety.safeReason(suggestion.reason()) : validation.reason();
        if (!validation.accepted() && !providerWarnings.isEmpty()) {
            finalReason = providerWarnings.get(0);
        }

        return new RegenerateBulletResponse(
                index,
                finalBullet,
                finalReason,
                !validation.accepted(),
                validation.accepted() ? null : normalizedDraft
        );
    }

    private List<String> deriveAllowedKeywords(List<KeywordScore> topKeywords) {
        if (topKeywords == null || topKeywords.isEmpty()) {
            return List.of();
        }
        return topKeywords.stream()
                .filter(KeywordScore::inResume)
                .map(KeywordScore::keyword)
                .limit(12)
                .toList();
    }
}
