package com.rajan.resumetailor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CoachChatMessage(
        @NotBlank(message = "role is required.")
        @Size(max = 16, message = "role is too large for this demo.")
        String role,

        @NotBlank(message = "content is required.")
        @Size(max = 4_000, message = "content is too large for this demo.")
        String content
) {
}

