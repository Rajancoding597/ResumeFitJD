package com.rajan.resumetailor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BulletOverride(
        @NotNull(message = "index is required.")
        @Min(value = 0, message = "index must be >= 0.")
        @Max(value = 500, message = "index is too large for this demo.")
        Integer index,

        @NotNull(message = "accepted is required.")
        Boolean accepted,

        @Size(max = 2_000, message = "revisedBullet is too large for this demo.")
        String revisedBullet
) {
}

