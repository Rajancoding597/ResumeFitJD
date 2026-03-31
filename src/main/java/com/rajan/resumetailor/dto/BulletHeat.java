package com.rajan.resumetailor.dto;

import java.util.List;

public record BulletHeat(
        String bulletId,
        List<String> matchedKeywords
) {
}

