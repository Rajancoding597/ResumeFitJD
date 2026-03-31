package com.rajan.resumetailor.service;

public record AiDocOptions(
        int maxBullets,
        String tone
) {
    public static AiDocOptions defaults() {
        return new AiDocOptions(40, "natural_professional");
    }
}

