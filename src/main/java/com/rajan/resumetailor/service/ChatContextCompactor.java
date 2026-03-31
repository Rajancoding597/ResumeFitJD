package com.rajan.resumetailor.service;

import com.rajan.resumetailor.config.AiPromptLimitsProperties;
import com.rajan.resumetailor.dto.CoachChatMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ChatContextCompactor {

    private final AiPromptLimitsProperties limits;
    private final TextLimiter textLimiter;

    public ChatContextCompactor(AiPromptLimitsProperties limits, TextLimiter textLimiter) {
        this.limits = limits;
        this.textLimiter = textLimiter;
    }

    public List<CoachChatMessage> compact(List<CoachChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        int maxMessages = Math.max(1, limits.getMaxChatHistoryMessages());
        int maxChars = Math.max(200, limits.getMaxChatMessageChars());

        List<CoachChatMessage> normalized = new ArrayList<>();
        for (CoachChatMessage m : messages) {
            if (m == null) continue;
            String role = normalizeRole(m.role());
            if (role.isBlank()) continue;
            String content = textLimiter.limit(m.content(), maxChars, "Chat message");
            if (content.isBlank()) continue;
            normalized.add(new CoachChatMessage(role, content));
        }

        if (normalized.size() <= maxMessages) {
            return normalized;
        }

        // Keep the most recent messages, but preserve older intent as a deterministic summary.
        int keepTail = Math.max(4, Math.min(maxMessages - 1, maxMessages));
        List<CoachChatMessage> tail = normalized.subList(normalized.size() - keepTail, normalized.size());
        List<CoachChatMessage> head = normalized.subList(0, normalized.size() - keepTail);

        String summary = buildSummary(head);
        AiCallWarnings.add("Chat history was compacted to reduce token usage.");

        List<CoachChatMessage> compacted = new ArrayList<>(maxMessages);
        compacted.add(new CoachChatMessage("assistant", summary));
        compacted.addAll(tail);
        return compacted.size() > maxMessages
                ? compacted.subList(compacted.size() - maxMessages, compacted.size())
                : compacted;
    }

    private String normalizeRole(String role) {
        if (role == null) return "";
        String r = role.trim().toLowerCase(Locale.ROOT);
        return (r.equals("user") || r.equals("assistant")) ? r : "";
    }

    private String buildSummary(List<CoachChatMessage> messages) {
        // Deterministic, cheap summary: last few user asks + last few assistant replies.
        List<String> user = new ArrayList<>();
        List<String> assistant = new ArrayList<>();
        for (CoachChatMessage m : messages) {
            if ("user".equalsIgnoreCase(m.role())) user.add(m.content());
            if ("assistant".equalsIgnoreCase(m.role())) assistant.add(m.content());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Conversation summary (older messages): ");

        appendLastN(sb, "User asked", user, 3);
        appendLastN(sb, "Assistant replied", assistant, 2);

        String out = sb.toString().trim();
        if (out.length() > 900) {
            out = out.substring(0, 900) + "…";
        }
        return out;
    }

    private void appendLastN(StringBuilder sb, String label, List<String> items, int n) {
        if (items.isEmpty()) return;
        sb.append(label).append(": ");
        int start = Math.max(0, items.size() - n);
        for (int i = start; i < items.size(); i++) {
            if (i > start) sb.append(" | ");
            sb.append(items.get(i));
        }
        sb.append(". ");
    }
}

