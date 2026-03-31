package com.rajan.resumetailor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ResumeCoachChatRequest(
        @NotBlank(message = "mode is required.")
        @Size(max = 12, message = "mode is too large for this demo.")
        String mode,

        @NotBlank(message = "jobDescription is required.")
        @Size(max = 20_000, message = "jobDescription is too large for this demo.")
        String jobDescription,

        @Size(max = 50_000, message = "resumeLatex is too large for this demo.")
        String resumeLatex,

        @NotNull(message = "bullets is required.")
        @Size(max = 600, message = "Too many bullets for this demo.")
        List<@Valid CoachBulletContext> bullets,

        @NotNull(message = "messages is required.")
        @Size(max = 60, message = "Too many messages for this demo.")
        List<@Valid CoachChatMessage> messages,

        @Valid KeywordInsights keywordInsights,
        @Valid AtsInsights atsInsights,
        @Valid CoachOptions options
) {
}

