package com.rajan.resumetailor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CoachOptions(
        @Min(value = 0, message = "maxEdits must be >= 0.")
        @Max(value = 8, message = "maxEdits is too large for this demo.")
        Integer maxEdits,

        @Size(max = 48, message = "tone is too large for this demo.")
        String tone
) {
}

