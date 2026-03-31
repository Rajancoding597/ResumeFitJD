package com.rajan.resumetailor.service;

import java.util.ArrayList;
import java.util.List;

public final class AiCallWarnings {

    private static final ThreadLocal<List<String>> WARNINGS = ThreadLocal.withInitial(ArrayList::new);

    private AiCallWarnings() {
    }

    public static void add(String warning) {
        if (warning == null || warning.isBlank()) {
            return;
        }
        WARNINGS.get().add(warning.trim());
    }

    public static void clear() {
        WARNINGS.get().clear();
    }

    public static List<String> drain() {
        List<String> current = new ArrayList<>(WARNINGS.get());
        WARNINGS.get().clear();
        return current;
    }
}

