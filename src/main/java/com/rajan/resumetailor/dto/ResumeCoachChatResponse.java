package com.rajan.resumetailor.dto;

import java.util.List;

public record ResumeCoachChatResponse(
        String assistantMessage,
        List<CoachAction> actions,
        List<String> warnings
) {
}

