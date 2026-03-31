package com.rajan.resumetailor.controller;

import com.rajan.resumetailor.dto.ResumeTailorRequest;
import com.rajan.resumetailor.dto.ResumeTailorResponse;
import com.rajan.resumetailor.dto.RegenerateBulletRequest;
import com.rajan.resumetailor.dto.RegenerateBulletResponse;
import com.rajan.resumetailor.dto.AiDocTailorResponse;
import com.rajan.resumetailor.dto.ResumeCoachChatRequest;
import com.rajan.resumetailor.dto.ResumeCoachChatResponse;
import com.rajan.resumetailor.service.ResumeTailorService;
import com.rajan.resumetailor.dto.ApplyBulletsRequest;
import com.rajan.resumetailor.dto.ApplyBulletsResponse;
import com.rajan.resumetailor.dto.ClientConfigResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.rajan.resumetailor.service.AiDocTailorService;
import com.rajan.resumetailor.service.GeminiApiKeyResolver;
import com.rajan.resumetailor.service.ResumeCoachChatService;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/resume-tailor")
public class ResumeTailorController {

    private final ResumeTailorService resumeTailorService;
    private final AiDocTailorService aiDocTailorService;
    private final ResumeCoachChatService resumeCoachChatService;
    private final GeminiApiKeyResolver geminiApiKeyResolver;
    private final boolean coachEnabled;

    public ResumeTailorController(
            ResumeTailorService resumeTailorService,
            AiDocTailorService aiDocTailorService,
            ResumeCoachChatService resumeCoachChatService,
            GeminiApiKeyResolver geminiApiKeyResolver,
            @Value("${feature.coach.enabled:false}") boolean coachEnabled
    ) {
        this.resumeTailorService = resumeTailorService;
        this.aiDocTailorService = aiDocTailorService;
        this.resumeCoachChatService = resumeCoachChatService;
        this.geminiApiKeyResolver = geminiApiKeyResolver;
        this.coachEnabled = coachEnabled;
    }

    @GetMapping("/client-config")
    public ClientConfigResponse clientConfig() {
        return new ClientConfigResponse(geminiApiKeyResolver.isUserKeyRequired());
    }

    @PostMapping
    public ResumeTailorResponse tailorResume(@Valid @RequestBody ResumeTailorRequest request) {
        return resumeTailorService.tailorResume(request);
    }

    @PostMapping("/regenerate-bullet")
    public RegenerateBulletResponse regenerateBullet(@Valid @RequestBody RegenerateBulletRequest request) {
        return resumeTailorService.regenerateBullet(request);
    }

    @PostMapping("/apply-bullets")
    public ApplyBulletsResponse applyBullets(@Valid @RequestBody ApplyBulletsRequest request) {
        return resumeTailorService.applyBullets(request);
    }

    @PostMapping(value = "/ai-doc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiDocTailorResponse tailorFromDocument(
            @RequestPart("resumeFile") MultipartFile resumeFile,
            @RequestPart("jobDescription") String jobDescription,
            @RequestPart(value = "options", required = false) String optionsJson
    ) {
        return aiDocTailorService.tailorFromAiDoc(resumeFile, jobDescription, optionsJson);
    }

    @PostMapping("/chat")
    public ResumeCoachChatResponse chat(@Valid @RequestBody ResumeCoachChatRequest request) {
        if (!coachEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Coach feature is disabled.");
        }
        return resumeCoachChatService.chat(request);
    }
}
