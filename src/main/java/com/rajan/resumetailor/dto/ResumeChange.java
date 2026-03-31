package com.rajan.resumetailor.dto;

public record ResumeChange(
        String originalBullet,
        String revisedBullet,
        String reason,
        boolean safetyRejected,
        String rejectedDraft
) {
}
