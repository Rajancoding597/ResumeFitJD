package com.rajan.resumetailor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CoachAction(
        String type,

        @Min(value = 0, message = "bulletIndex must be >= 0.")
        @Max(value = 500, message = "bulletIndex is too large for this demo.")
        Integer bulletIndex,

        @Size(max = 2_000, message = "suggestedText is too large for this demo.")
        String suggestedText,

        @Size(max = 400, message = "reason is too large for this demo.")
        String reason,

        @Size(max = 60, message = "Too many keywords for this demo.")
        List<@Size(max = 64, message = "keyword is too large for this demo.") String> keywords,

        @Size(max = 400, message = "question is too large for this demo.")
        String question,

        Boolean safetyRejected,
        String rejectedDraft
) {
}

