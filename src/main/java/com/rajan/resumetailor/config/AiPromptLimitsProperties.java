package com.rajan.resumetailor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.limits")
public class AiPromptLimitsProperties {

    private int maxJobDescriptionChars = 12_000;
    private int maxBulletChars = 1_200;
    private int maxChatMessageChars = 1_200;
    private int maxChatHistoryMessages = 12;

    public int getMaxJobDescriptionChars() {
        return maxJobDescriptionChars;
    }

    public void setMaxJobDescriptionChars(int maxJobDescriptionChars) {
        this.maxJobDescriptionChars = maxJobDescriptionChars;
    }

    public int getMaxBulletChars() {
        return maxBulletChars;
    }

    public void setMaxBulletChars(int maxBulletChars) {
        this.maxBulletChars = maxBulletChars;
    }

    public int getMaxChatMessageChars() {
        return maxChatMessageChars;
    }

    public void setMaxChatMessageChars(int maxChatMessageChars) {
        this.maxChatMessageChars = maxChatMessageChars;
    }

    public int getMaxChatHistoryMessages() {
        return maxChatHistoryMessages;
    }

    public void setMaxChatHistoryMessages(int maxChatHistoryMessages) {
        this.maxChatHistoryMessages = maxChatHistoryMessages;
    }
}

