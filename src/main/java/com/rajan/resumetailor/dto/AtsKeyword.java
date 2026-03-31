package com.rajan.resumetailor.dto;

import java.util.List;

public record AtsKeyword(
        String keyword,
        boolean present,
        List<String> evidenceBulletIds,
        List<String> suggestionBulletIds
) {
}

