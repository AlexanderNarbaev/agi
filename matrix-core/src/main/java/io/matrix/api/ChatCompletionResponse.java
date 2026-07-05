package io.matrix.api;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import java.util.UUID;

/**
 * OpenAI-compatible chat completion response DTO.
 *
 * <p>Mirrors the exact structure that OpenAI clients expect.
 * Every field name matches the OpenAI API specification.
 */
@RegisterForReflection
public class ChatCompletionResponse {

    /** Unique completion identifier. */
    public String id;

    /** Always "chat.completion" per OpenAI spec. */
    public String object = "chat.completion";

    /** Unix timestamp (seconds) of creation. */
    public long created;

    /** Model used for this completion. */
    public String model;

    /** List of response choices (typically one). */
    public List<Choice> choices;

    /** Token usage statistics. */
    public Usage usage;

    public ChatCompletionResponse() {}

    /** A single completion choice. */
    @RegisterForReflection
    public static class Choice {
        /** Index of this choice (0-based). */
        public int index;

        /** The response message. */
        public Message message;

        /** Reason for completion: "stop", "length", or "content_filter". */
        public String finish_reason;

        public Choice() {}
    }

    /** A chat message with role and content. */
    @RegisterForReflection
    public static class Message {
        /** Always "assistant" for responses. */
        public String role = "assistant";

        /** The response text. */
        public String content;

        public Message() {}
    }

    /** Token usage tracking (approximate for MPDT). */
    @RegisterForReflection
    public static class Usage {
        /** Approximate input token count. */
        public int prompt_tokens;

        /** Approximate output token count. */
        public int completion_tokens;

        /** Total tokens used. */
        public int total_tokens;

        public Usage() {}
    }

    // ─── Factory methods ───

    /** Creates a response with the given content and model. */
    public static ChatCompletionResponse of(String content, String model) {
        ChatCompletionResponse resp = new ChatCompletionResponse();
        resp.id = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);
        resp.created = System.currentTimeMillis() / 1000;
        resp.model = model;

        Choice choice = new Choice();
        choice.index = 0;
        choice.finish_reason = "stop";

        Message msg = new Message();
        msg.content = content;
        choice.message = msg;

        resp.choices = List.of(choice);

        Usage usage = new Usage();
        usage.prompt_tokens = estimateTokens(content);
        usage.completion_tokens = 0;
        usage.total_tokens = usage.prompt_tokens;
        resp.usage = usage;

        return resp;
    }

    /** Creates a refusal response (ethical filter rejection). */
    public static ChatCompletionResponse refuse(String reason, String model) {
        ChatCompletionResponse resp = new ChatCompletionResponse();
        resp.id = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);
        resp.created = System.currentTimeMillis() / 1000;
        resp.model = model;

        Choice choice = new Choice();
        choice.index = 0;
        choice.finish_reason = "content_filter";

        Message msg = new Message();
        msg.content = reason;
        choice.message = msg;

        resp.choices = List.of(choice);

        Usage usage = new Usage();
        usage.prompt_tokens = 0;
        usage.completion_tokens = estimateTokens(reason);
        usage.total_tokens = usage.completion_tokens;
        resp.usage = usage;

        return resp;
    }

    private static int estimateTokens(String text) {
        if (text == null) return 0;
        return text.split("\\s+").length;
    }
}
