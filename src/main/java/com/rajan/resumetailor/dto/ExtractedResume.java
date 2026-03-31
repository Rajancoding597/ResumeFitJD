package com.rajan.resumetailor.dto;

import java.util.List;

public record ExtractedResume(
        List<ExtractedBullet> bullets,
        String rawTextPreview
) {
}

