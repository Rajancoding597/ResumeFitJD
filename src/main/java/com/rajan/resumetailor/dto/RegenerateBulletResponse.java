package com.rajan.resumetailor.dto;

public record RegenerateBulletResponse(
        int bulletIndex,
        String revisedBullet,
        String reason,
        boolean safetyRejected,
        String rejectedDraft
) {
}

