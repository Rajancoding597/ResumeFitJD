package com.rajan.resumetailor.dto;

public record KeywordScore(
        String keyword,
        double weight,
        boolean inResume
) {
}

