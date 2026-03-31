package com.rajan.resumetailor.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LatexResumeService {

    private static final Pattern COMMAND_PATTERN = Pattern.compile(
            "(?m)^\\s*(\\\\begin\\{([^}]+)\\}|\\\\end\\{([^}]+)\\}|\\\\item\\b|\\\\resumeItemListEnd\\b)"
    );
    private static final List<String> MACRO_BULLET_COMMANDS = List.of("\\resumeItem{", "\\cvItem{");
    private static final Pattern TRAILING_LAYOUT_PATTERN = Pattern.compile(
            "(?s)(?:\\s*(?:(?<!\\\\)%[^\\r\\n]*(?:\\R|$)|\\\\par\\b\\s*(?:\\R|$)|\\\\(?:smallskip|medskip|bigskip|noindent)\\b\\s*(?:\\R|$)|\\\\vspace\\*?\\{[^}]*}\\s*(?:\\R|$)))+$"
    );

    public ParsedResume parse(String latex) {
        List<BulletSegment> segments = parseItemBlocks(latex);
        if (segments.isEmpty()) {
            segments = parseMacroBullets(latex);
        }
        return new ParsedResume(latex, segments);
    }

    public String merge(ParsedResume parsedResume, List<String> revisedBullets) {
        if (parsedResume.bullets().size() != revisedBullets.size()) {
            throw new IllegalArgumentException("Bullet count does not match rewrite count.");
        }

        StringBuilder merged = new StringBuilder(parsedResume.originalLatex());
        for (int index = parsedResume.bullets().size() - 1; index >= 0; index--) {
            BulletSegment segment = parsedResume.bullets().get(index);
            merged.replace(segment.contentStart(), segment.contentEnd(), segment.render(revisedBullets.get(index)));
        }
        return merged.toString();
    }

    private List<BulletSegment> parseItemBlocks(String latex) {
        List<CommandToken> tokens = collectTokens(latex);
        if (tokens.stream().noneMatch(token -> token.type() == TokenType.ITEM)) {
            return List.of();
        }

        List<BulletSegment> segments = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            CommandToken token = tokens.get(index);
            if (token.type() != TokenType.ITEM) {
                continue;
            }

            int rawStart = token.end();
            int rawEnd = findItemBoundary(tokens, index, latex.length());

            BulletSegment segment = createSegment(rawStart, rawEnd, latex);
            if (segment != null) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private List<BulletSegment> parseMacroBullets(String latex) {
        List<BulletSegment> segments = new ArrayList<>();
        for (String command : MACRO_BULLET_COMMANDS) {
            int searchFrom = 0;
            while (searchFrom < latex.length()) {
                int commandStart = latex.indexOf(command, searchFrom);
                if (commandStart < 0) {
                    break;
                }
                int contentStart = commandStart + command.length();
                int contentEnd = findMatchingBrace(latex, contentStart - 1);
                if (contentEnd > contentStart) {
                    BulletSegment segment = createSegment(contentStart, contentEnd, latex);
                    if (segment != null) {
                        segments.add(segment);
                    }
                    searchFrom = contentEnd + 1;
                } else {
                    searchFrom = contentStart;
                }
            }
        }
        return segments.stream()
                .sorted(Comparator.comparingInt(BulletSegment::contentStart))
                .toList();
    }

    private List<CommandToken> collectTokens(String source) {
        List<CommandToken> tokens = new ArrayList<>();
        List<String> environmentStack = new ArrayList<>();

        Matcher matcher = COMMAND_PATTERN.matcher(source);
        while (matcher.find()) {
            String command = matcher.group(1);
            String beginEnvironment = matcher.group(2);
            String endEnvironment = matcher.group(3);
            List<String> environmentBefore = List.copyOf(environmentStack);

            CommandToken token;
            if (beginEnvironment != null) {
                token = new CommandToken(TokenType.BEGIN_ENV, matcher.start(1), matcher.end(1), beginEnvironment, environmentBefore);
                environmentStack.add(beginEnvironment);
            } else if (endEnvironment != null) {
                token = new CommandToken(TokenType.END_ENV, matcher.start(1), matcher.end(1), endEnvironment, environmentBefore);
                popEnvironment(environmentStack, endEnvironment);
            } else if ("\\resumeItemListEnd".equals(command)) {
                token = new CommandToken(TokenType.STANDALONE_END, matcher.start(1), matcher.end(1), "resumeItemListEnd", environmentBefore);
            } else {
                token = new CommandToken(TokenType.ITEM, matcher.start(1), matcher.end(1), null, environmentBefore);
            }

            tokens.add(token);
        }

        return tokens;
    }

    private int findItemBoundary(List<CommandToken> tokens, int itemIndex, int defaultValue) {
        CommandToken currentItem = tokens.get(itemIndex);
        List<String> currentEnvironmentStack = currentItem.environmentBefore();
        String currentEnvironment = currentEnvironmentStack.isEmpty()
                ? null
                : currentEnvironmentStack.get(currentEnvironmentStack.size() - 1);

        for (int index = itemIndex + 1; index < tokens.size(); index++) {
            CommandToken next = tokens.get(index);
            if (next.type() == TokenType.ITEM && next.environmentBefore().equals(currentEnvironmentStack)) {
                return next.start();
            }

            if (next.type() == TokenType.STANDALONE_END && next.environmentBefore().equals(currentEnvironmentStack)) {
                return next.start();
            }

            if (currentEnvironment != null
                    && next.type() == TokenType.END_ENV
                    && next.environmentBefore().equals(currentEnvironmentStack)
                    && currentEnvironment.equals(next.environmentName())) {
                return next.start();
            }
        }

        return defaultValue;
    }

    private void popEnvironment(List<String> environmentStack, String environmentName) {
        for (int index = environmentStack.size() - 1; index >= 0; index--) {
            if (environmentName.equals(environmentStack.get(index))) {
                environmentStack.remove(index);
                return;
            }
        }
    }

    private int findMatchingBrace(String source, int openingBraceIndex) {
        int depth = 0;
        for (int index = openingBraceIndex; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private BulletSegment createSegment(int rawStart, int rawEnd, String source) {
        if (rawEnd <= rawStart) {
            return null;
        }

        String raw = source.substring(rawStart, rawEnd);
        Matcher trailingLayoutMatcher = TRAILING_LAYOUT_PATTERN.matcher(raw);
        if (trailingLayoutMatcher.find()) {
            rawEnd = rawStart + trailingLayoutMatcher.start();
            raw = source.substring(rawStart, rawEnd);
        }

        String originalBullet = raw.trim();
        if (originalBullet.isBlank()) {
            return null;
        }

        int leadingWhitespaceLength = 0;
        while (leadingWhitespaceLength < raw.length() && Character.isWhitespace(raw.charAt(leadingWhitespaceLength))) {
            leadingWhitespaceLength++;
        }

        int trailingWhitespaceLength = 0;
        while (trailingWhitespaceLength < raw.length()
                && Character.isWhitespace(raw.charAt(raw.length() - 1 - trailingWhitespaceLength))) {
            trailingWhitespaceLength++;
        }

        String leadingWhitespace = raw.substring(0, leadingWhitespaceLength);
        String trailingWhitespace = trailingWhitespaceLength == 0
                ? ""
                : raw.substring(raw.length() - trailingWhitespaceLength);

        return new BulletSegment(rawStart, rawEnd, originalBullet, leadingWhitespace, trailingWhitespace);
    }

    private enum TokenType {
        BEGIN_ENV,
        END_ENV,
        ITEM,
        STANDALONE_END
    }

    private record CommandToken(
            TokenType type,
            int start,
            int end,
            String environmentName,
            List<String> environmentBefore
    ) {
    }

    public record ParsedResume(String originalLatex, List<BulletSegment> bullets) {
    }

    public record BulletSegment(
            int contentStart,
            int contentEnd,
            String originalBullet,
            String leadingWhitespace,
            String trailingWhitespace
    ) {
        public String render(String revisedBullet) {
            return leadingWhitespace + revisedBullet + trailingWhitespace;
        }
    }
}
