package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rajan.resumetailor.config.AiPromptLimitsProperties;
import com.rajan.resumetailor.dto.CoachChatMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatContextCompactorTest {

    @Test
    void compactsLongHistoryIntoSummaryPlusTail() {
        AiPromptLimitsProperties limits = new AiPromptLimitsProperties();
        limits.setMaxChatHistoryMessages(6);
        limits.setMaxChatMessageChars(120);
        ChatContextCompactor compactor = new ChatContextCompactor(limits, new TextLimiter());

        List<CoachChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(new CoachChatMessage(i % 2 == 0 ? "user" : "assistant", "Message " + i + " with extra words."));
        }

        var out = compactor.compact(messages);
        assertEquals(6, out.size());
        assertEquals("assistant", out.get(0).role());
        assertTrue(out.get(0).content().toLowerCase().contains("conversation summary"));
    }
}

