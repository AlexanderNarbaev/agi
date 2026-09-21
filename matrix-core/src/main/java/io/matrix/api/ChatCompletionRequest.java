package io.matrix.api;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

/**
 * OpenAI-compatible chat completion request DTO.
 *
 * <p>Supports the standard fields that OpenAI clients send.
 * Temperature is parsed for compatibility but ignored — MPDT neurons
 * are deterministic, not probabilistic.
 */
@RegisterForReflection
public class ChatCompletionRequest {

    /** Model identifier: "mpdt-smollm2" or "mpdt-qwen". */
    public String model;

    /** Chat messages with roles (user, assistant, system). */
    public List<Message> messages;

    /** Ignored — MPDT neurons are deterministic. */
    public double temperature;

    /** Maximum tokens in the response (maps to max response length). */
    public int max_tokens;

    /** Streaming not yet supported; parsed for compatibility. */
    public boolean stream;

    public ChatCompletionRequest() {}

    /** A single chat message with role and content. */
    @RegisterForReflection
    public static class Message {
        /** Role: "user", "assistant", or "system". */
        public String role;

        /** Message text content. */
        public String content;

        public Message() {}
    }
}
