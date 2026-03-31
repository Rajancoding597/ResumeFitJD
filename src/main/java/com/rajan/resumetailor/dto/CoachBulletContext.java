package com.rajan.resumetailor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CoachBulletContext(
        @NotNull(message = "index is required.")
        @Min(value = 0, message = "index must be >= 0.")
        @Max(value = 600, message = "index is too large for this demo.")
        Integer index,

        @Size(max = 32, message = "bulletId is too large for this demo.")
        String bulletId,

        @NotBlank(message = "original is required.")
        @Size(max = 2_000, message = "original is too large for this demo.")
        String original,

        @NotBlank(message = "current is required.")
        @Size(max = 2_000, message = "current is too large for this demo.")
        String current,

        @NotNull(message = "accepted is required.")
        Boolean accepted,

        @NotNull(message = "safetyRejected is required.")
        Boolean safetyRejected
) {
}

