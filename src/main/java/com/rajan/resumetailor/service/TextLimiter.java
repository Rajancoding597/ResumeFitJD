package com.rajan.resumetailor.service;

import org.springframework.stereotype.Component;

@Component
public class TextLimiter {

    public String limit(String value, int maxChars, String warningLabel) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (maxChars <= 0 || trimmed.length() <= maxChars) {
            return trimmed;
        }
        int keep = Math.max(0, maxChars);
        AiCallWarnings.add(warningLabel + " was truncated to " + maxChars + " characters for this demo.");
        return trimmed.substring(0, keep) + "…";
    }

    public String limitNoEllipsis(String value, int maxChars, String warningLabel) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (maxChars <= 0 || trimmed.length() <= maxChars) {
            return trimmed;
        }
        AiCallWarnings.add(warningLabel + " was truncated to " + maxChars + " characters for this demo.");
        return trimmed.substring(0, Math.max(0, maxChars));
    }
}

