package io.matrix.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Single recorded chat interaction between a user and the MATRIX bot.
 *
 * <p>Captured at every OpenAI-compatible chat completion call. Persisted to
 * {@code data/conversations/} as newline-delimited JSON for streaming-friendly
 * ingestion into the training pipeline.
 *
 * <p>The full conversation is reconstructed by grouping records by
 * {@link #conversationId()} and ordering by {@link #timestamp()}.
 *
 * <p>Ref: Wave 35 — autonomous dialogue training loop.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationRecord(
        String conversationId,
        String role,
        String content,
        String userId,
        String model,
        String ethicalVerdict,
        Long sensorBits,
        Instant timestamp,
        Double responseTimeMs,
        Integer tokensApprox
) {

    public static ConversationRecord user(String conversationId, String userId,
                                          String model, String content) {
        return new ConversationRecord(
                conversationId, "user", content, userId, model,
                null, null, Instant.now(), null, null);
    }

    public static ConversationRecord assistant(String conversationId, String userId,
                                               String model, String content,
                                               String ethicalVerdict,
                                               long sensorBits,
                                               double responseTimeMs,
                                               int tokensApprox) {
        return new ConversationRecord(
                conversationId, "assistant", content, userId, model,
                ethicalVerdict, sensorBits, Instant.now(),
                responseTimeMs, tokensApprox);
    }

    public static ConversationRecord system(String conversationId, String content) {
        return new ConversationRecord(
                conversationId, "system", content, null, null,
                null, null, Instant.now(), null, null);
    }

    public static String newConversationId() {
        return "conv-" + UUID.randomUUID();
    }
}