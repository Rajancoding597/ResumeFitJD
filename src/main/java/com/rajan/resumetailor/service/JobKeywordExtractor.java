package com.rajan.resumetailor.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class JobKeywordExtractor {

    // Keeps typical technical tokens like C++, C#, .NET, Kafka, Kubernetes, AWS, GCP, CI/CD, OAuth2, etc.
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?i)(?:\\.[a-z][a-z0-9+#.\\-/]{1,}|[a-z][a-z0-9+#.\\-/]{1,}|[a-z]\\+\\+|c#)"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "into", "of", "on", "or",
            "the", "to", "with", "using", "used", "through", "across", "over", "via", "within", "while",
            "you", "your", "we", "our", "they", "them", "he", "she", "it", "this", "that", "these", "those",
            "will", "would", "should", "can", "could", "may", "might", "must", "able", "ability",
            "look", "looking", "seek", "seeking", "hiring", "hire", "joins", "join",
            "experience", "years", "year", "month", "months", "day", "days", "role", "roles", "work", "working",
            "responsible", "responsibilities", "required", "requirements", "preferred", "skills", "skill",
            "knowledge", "understanding", "strong", "excellent", "good", "nice", "have", "has", "had"
    );

    public List<WeightedKeyword> extractTopKeywords(String jobDescription, int limit) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return List.of();
        }

        Map<String, Integer> frequencies = new HashMap<>();
        Matcher matcher = TOKEN_PATTERN.matcher(jobDescription);
        while (matcher.find()) {
            String token = normalize(matcher.group());
            if (token.isBlank()) {
                continue;
            }
            if (token.length() < 2) {
                continue;
            }
            if (STOP_WORDS.contains(token)) {
                continue;
            }
            frequencies.merge(token, 1, Integer::sum);
        }

        if (frequencies.isEmpty()) {
            return List.of();
        }

        Set<String> seen = new HashSet<>();
        List<WeightedKeyword> weighted = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            String keyword = entry.getKey();
            if (!seen.add(keyword)) {
                continue;
            }
            int count = entry.getValue();
            // Simple term frequency with a gentle cap to reduce spam.
            double weight = Math.min(5.0d, 1.0d + Math.log(1.0d + count));
            weighted.add(new WeightedKeyword(keyword, weight));
        }

        return weighted.stream()
                .sorted(Comparator.comparingDouble(WeightedKeyword::weight).reversed()
                        .thenComparing(WeightedKeyword::keyword))
                .limit(Math.max(0, limit))
                .toList();
    }

    private String normalize(String token) {
        return token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
    }

    public record WeightedKeyword(String keyword, double weight) {
    }
}
