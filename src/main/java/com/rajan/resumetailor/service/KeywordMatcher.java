package com.rajan.resumetailor.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class KeywordMatcher {

    private static final Pattern SIMPLE_WORD = Pattern.compile("^[a-z0-9]+$");

    public boolean containsKeyword(String text, String keyword) {
        String resumeTextLower = String.valueOf(text).toLowerCase();
        String keywordLower = String.valueOf(keyword).toLowerCase();

        if (resumeTextLower.isBlank() || keywordLower.isBlank()) {
            return false;
        }

        // For non-alphanumeric tokens like "c++", ".net", or "node.js", a substring match is good enough.
        if (!SIMPLE_WORD.matcher(keywordLower).matches()) {
            return resumeTextLower.contains(keywordLower);
        }

        int fromIndex = 0;
        while (true) {
            int index = resumeTextLower.indexOf(keywordLower, fromIndex);
            if (index < 0) {
                return false;
            }

            boolean leftOk = index == 0 || !Character.isLetterOrDigit(resumeTextLower.charAt(index - 1));
            int rightIndex = index + keywordLower.length();
            boolean rightOk = rightIndex >= resumeTextLower.length()
                    || !Character.isLetterOrDigit(resumeTextLower.charAt(rightIndex));
            if (leftOk && rightOk) {
                return true;
            }

            // Keep searching in case the first occurrence wasn't boundary-safe.
            fromIndex = index + 1;
        }
    }
}

