package com.rajan.resumetailor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResumeTailorRequest(
        @NotBlank(message = "Resume LaTeX is required.")
        @Size(max = 50_000, message = "Resume LaTeX is too large for this demo.")
        String resumeLatex,
        @NotBlank(message = "Job description is required.")
        @Size(max = 20_000, message = "Job description is too large for this demo.")
        String jobDescription
) {
}

