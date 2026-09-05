package io.matrix.api;

import java.util.List;

/**
 * RUN 124 — Context window manager.
 *
 * <p>Trims a message list to fit within a maximum token budget by
 * keeping the most recent messages. Estimates token count from
 * character length.
 */
public final class ContextWindowManager {

    private final int maxTokens;

    public ContextWindowManager(int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be > 0");
        }
        this.maxTokens = maxTokens;
    }

    public int maxTokens() { return maxTokens; }

    /**
     * Estimate token count for a message (rough: 4 chars per token).
     */
    public static int estimateTokens(QwenChatTemplate.Message msg) {
        return (msg.content().length() + 3) / 4 + 4;  // content + markers
    }

    /**
     * Trim the message list to fit within the token budget, keeping
     * the most recent messages (system message is preserved).
     */
    public List<QwenChatTemplate.Message> trim(List<QwenChatTemplate.Message> messages) {
        int total = 0;
        for (var m : messages) total += estimateTokens(m);
        if (total <= maxTokens) return messages;

        // Build new list: keep system message (if first) + most recent
        java.util.List<QwenChatTemplate.Message> result = new java.util.ArrayList<>();
        QwenChatTemplate.Message systemMsg = null;
        if (!messages.isEmpty()
                && messages.get(0).role() == QwenChatTemplate.Role.SYSTEM) {
            systemMsg = messages.get(0);
        }
        // Walk from the end, accumulating tokens
        int budget = maxTokens;
        if (systemMsg != null) {
            int sysTokens = estimateTokens(systemMsg);
            if (sysTokens <= budget) {
                result.add(0, systemMsg);
                budget -= sysTokens;
            }
        }
        // Add messages from the end (newest first) until budget exhausted
        for (int i = messages.size() - 1; i >= (systemMsg != null ? 1 : 0); i--) {
            var m = messages.get(i);
            int tokens = estimateTokens(m);
            if (tokens <= budget) {
                if (systemMsg != null) {
                    result.add(1, m);  // insert after system message
                } else {
                    result.add(m);
                }
                budget -= tokens;
            } else {
                break;
            }
        }
        return result;
    }
}
