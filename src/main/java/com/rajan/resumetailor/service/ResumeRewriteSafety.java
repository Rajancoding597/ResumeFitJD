package com.rajan.resumetailor.service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ResumeRewriteSafety {

    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z][A-Za-z+#.\\-]{1,}");
    private static final Pattern NUMERIC_CLAIM_PATTERN = Pattern.compile("(?i)\\b(\\d[\\d,]*(?:\\.\\d+)?)\\s*(%|x)?\\b");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "into", "of", "on", "or",
            "the", "to", "with", "using", "used", "through", "across", "over", "via", "within", "while"
    );

    public ValidationOutcome validateSuggestion(String original, String revised) {
        String cleaned = normalizeInlineWhitespace(revised);
        if (cleaned.isBlank()) {
            return ValidationOutcome.reject("Original bullet kept because the AI rewrite was blank.");
        }

        if (cleaned.length() > Math.max(original.length() * 2, original.length() + 60)) {
            return ValidationOutcome.reject("Original bullet kept because the rewrite became unusually long.");
        }

        if (introducesNewNumbers(original, cleaned)) {
            return ValidationOutcome.reject("Original bullet kept because the rewrite introduced unsupported numeric claims.");
        }

        if (lexicalOverlap(original, cleaned) < 0.35d) {
            return ValidationOutcome.reject("Original bullet kept because the rewrite drifted too far from the source bullet.");
        }

        return ValidationOutcome.accept();
    }

    public String normalizeInlineWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public String safeReason(String reason) {
        String cleaned = normalizeInlineWhitespace(reason);
        return cleaned.isBlank()
                ? "Reworded to better match the job description while staying faithful to the original claim."
                : cleaned;
    }

    private boolean introducesNewNumbers(String original, String revised) {
        Set<String> originalClaims = extractNumericClaims(original);
        Set<String> revisedClaims = extractNumericClaims(revised);
        return !originalClaims.containsAll(revisedClaims);
    }

    private Set<String> extractNumericClaims(String input) {
        if (input == null || input.isBlank()) {
            return Set.of();
        }

        // Normalize common equivalent formatting so we don't reject "30\\%" vs "30%" or "1.5×" vs "1.5x".
        String normalized = input
                .replace("\\%", "%")
                .replace('×', 'x');

        Set<String> claims = new HashSet<>();
        Matcher matcher = NUMERIC_CLAIM_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String numberRaw = matcher.group(1);
            String unit = matcher.group(2);

            String canonicalNumber = numberRaw == null ? "" : numberRaw.replace(",", "");
            if (canonicalNumber.isBlank()) {
                continue;
            }

            String canonicalUnit = unit == null ? "" : unit.toLowerCase(Locale.ROOT).trim();
            claims.add(canonicalNumber + canonicalUnit);
        }
        return claims;
    }

    private double lexicalOverlap(String original, String revised) {
        Set<String> originalKeywords = collectKeywords(original);
        if (originalKeywords.isEmpty()) {
            return 1.0d;
        }

        Set<String> revisedKeywords = collectKeywords(revised);
        Set<String> shared = new HashSet<>(originalKeywords);
        shared.retainAll(revisedKeywords);
        return (double) shared.size() / (double) originalKeywords.size();
    }

    private Set<String> collectKeywords(String input) {
        Set<String> keywords = new HashSet<>();
        Matcher matcher = WORD_PATTERN.matcher(input.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (!STOP_WORDS.contains(word)) {
                keywords.add(word);
            }
        }
        return keywords;
    }

    private Set<String> collectMatches(Pattern pattern, String input) {
        Set<String> matches = new HashSet<>();
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            matches.add(matcher.group());
        }
        return matches;
    }

    public record ValidationOutcome(boolean accepted, String reason) {
        public static ValidationOutcome accept() {
            return new ValidationOutcome(true, "");
        }

        public static ValidationOutcome reject(String reason) {
            return new ValidationOutcome(false, reason);
        }
    }
}
