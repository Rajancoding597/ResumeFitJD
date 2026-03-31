package com.rajan.resumetailor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ApplyBulletsRequest(
        @NotBlank(message = "Resume LaTeX is required.")
        @Size(max = 50_000, message = "Resume LaTeX is too large for this demo.")
        String resumeLatex,

        @NotNull(message = "overrides is required.")
        @Size(max = 600, message = "Too many bullet overrides for this demo.")
        List<@Valid BulletOverride> overrides
) {
}

