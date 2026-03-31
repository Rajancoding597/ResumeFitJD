package com.rajan.resumetailor.service;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class LatexPlainTextExtractor {

    private static final Pattern COMMENTS = Pattern.compile("(?m)(?<!\\\\)%.*$");
    private static final Pattern BEGIN_END = Pattern.compile("(?s)\\\\(?:begin|end)\\{[^}]+\\}");
    private static final Pattern COMMAND_WITH_BRACE = Pattern.compile("(?s)\\\\[A-Za-z@]+\\*?\\s*\\{");
    private static final Pattern COMMAND_NO_BRACE = Pattern.compile("(?m)\\\\[A-Za-z@]+\\*?\\b");
    private static final Pattern MATH_INLINE = Pattern.compile("\\$[^$]*\\$");

    public String extract(String latex) {
        if (latex == null || latex.isBlank()) {
            return "";
        }

        String text = latex;
        text = COMMENTS.matcher(text).replaceAll(" ");
        text = BEGIN_END.matcher(text).replaceAll(" ");
        text = MATH_INLINE.matcher(text).replaceAll(" ");

        // Unwrap common escaped characters.
        text = text.replace("\\%", "%")
                .replace("\\&", "&")
                .replace("\\_", "_")
                .replace("\\#", "#")
                .replace("\\$", "$");

        // Remove commands while trying to keep brace content by only stripping the command token itself.
        text = COMMAND_WITH_BRACE.matcher(text).replaceAll("{");
        text = COMMAND_NO_BRACE.matcher(text).replaceAll(" ");

        // Collapse braces but keep contents.
        text = text.replace("{", " ").replace("}", " ");

        // Normalize whitespace.
        text = text.replaceAll("\\s+", " ").trim();
        return text.toLowerCase(Locale.ROOT);
    }

    /**
     * Extract plain text for AI prompts while preserving the original case.
     * This avoids teaching the model LaTeX syntax, but keeps names like "Oracle IAM" readable.
     */
    public String extractForAi(String latex) {
        if (latex == null || latex.isBlank()) {
            return "";
        }

        String text = latex;
        text = COMMENTS.matcher(text).replaceAll(" ");
        text = BEGIN_END.matcher(text).replaceAll(" ");
        text = MATH_INLINE.matcher(text).replaceAll(" ");

        text = text.replace("\\%", "%")
                .replace("\\&", "&")
                .replace("\\_", "_")
                .replace("\\#", "#")
                .replace("\\$", "$");

        text = COMMAND_WITH_BRACE.matcher(text).replaceAll("{");
        text = COMMAND_NO_BRACE.matcher(text).replaceAll(" ");
        text = text.replace("{", " ").replace("}", " ");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }
}
