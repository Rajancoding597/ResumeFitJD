package com.rajan.resumetailor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegenerateBulletRequest(
        @NotBlank(message = "Resume LaTeX is required.")
        @Size(max = 50_000, message = "Resume LaTeX is too large for this demo.")
        String resumeLatex,

        @NotBlank(message = "Job description is required.")
        @Size(max = 20_000, message = "Job description is too large for this demo.")
        String jobDescription,

        @NotNull(message = "bulletIndex is required.")
        @Min(value = 0, message = "bulletIndex must be >= 0.")
        @Max(value = 500, message = "bulletIndex is too large for this demo.")
        Integer bulletIndex,

        @NotBlank(message = "currentBulletText is required.")
        @Size(max = 2_000, message = "currentBulletText is too large for this demo.")
        String currentBulletText
) {
}

