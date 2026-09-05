package io.matrix.api;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 72 — Qwen2.5-Instruct chat template formatter.
 *
 * <p>Qwen2.5-Instruct uses the ChatML format:
 * <pre>{@code
 * <|im_start|>system
 * You are a helpful assistant.<|im_end|>
 * <|im_start|>user
 * Hello<|im_end|>
 * <|im_start|>assistant
 * }</pre>
 *
 * <p>This utility builds the formatted prompt from a list of messages.
 *
 * <p>Special tokens (from Qwen2.5 tokenizer_config.json):
 * <ul>
 *   <li>{@code <|im_start|>}: 151644 (role marker)</li>
 *   <li>{@code <|im_end|>}: 151645 (message terminator / EOS)</li>
 *   <li>{@code <|endoftext|>}: 151643 (PAD)</li>
 * </ul>
 */
public final class QwenChatTemplate {

    /** Token id for {@code <|im_start|>}. */
    public static final int IM_START = 151644;
    /** Token id for {@code <|im_end|>} — also EOS. */
    public static final int IM_END = 151645;
    /** Token id for {@code <|endoftext|>} — also PAD. */
    public static final int ENDOFTEXT = 151643;

    public enum Role {SYSTEM, USER, ASSISTANT}

    public record Message(Role role, String content) {
        public static Message system(String c) { return new Message(Role.SYSTEM, c); }
        public static Message user(String c) { return new Message(Role.USER, c); }
        public static Message assistant(String c) { return new Message(Role.ASSISTANT, c); }
    }

    private QwenChatTemplate() {}

    /**
     * Build a ChatML-formatted prompt from a list of messages.
     *
     * <p>Trailing {@code <|im_start|>assistant\n} is included so the
     * model continues from the assistant role.
     *
     * @param messages list of system/user/assistant turns
     * @return formatted prompt string ready for tokenization
     */
    public static String buildPrompt(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            sb.append("<|im_start|>").append(m.role.name().toLowerCase())
              .append('\n')
              .append(m.content())
              .append("<|im_end|>\n");
        }
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }

    /**
     * Convenience: build a single-turn user prompt with default system message.
     */
    public static String buildUserPrompt(String userMessage) {
        return buildPrompt(List.of(
                Message.system("You are a helpful assistant."),
                Message.user(userMessage)
        ));
    }

    /**
     * Strip trailing ChatML markers from a generated reply.
     * Useful for cleaning model output that includes extra tags.
     */
    public static String cleanReply(String raw) {
        if (raw == null) return null;
        String cleaned = raw;
        // Remove trailing <|im_end|> and similar
        int idx;
        if ((idx = cleaned.indexOf("<|im_end|>")) >= 0) {
            cleaned = cleaned.substring(0, idx);
        }
        if ((idx = cleaned.indexOf("<|endoftext|>")) >= 0) {
            cleaned = cleaned.substring(0, idx);
        }
        // Strip trailing assistant marker if model emits it
        if (cleaned.startsWith("<|im_start|>assistant\n")) {
            cleaned = cleaned.substring("<|im_start|>assistant\n".length());
        }
        return cleaned.strip();
    }

    /**
     * Count tokens in a list of messages (heuristic: chars/4 plus overhead).
     */
    public static int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message m : messages) {
            total += (m.content().length() + 3) / 4;  // ~4 chars/token
            total += 4;  // <|im_start|>, role, \n, <|im_end|>\n
        }
        return total;
    }

    /** Helper for tests: parse a list of messages into a ChatML string. */
    public static String render(List<Message> messages) {
        List<String> parts = new ArrayList<>();
        for (Message m : messages) {
            parts.add(m.role.name().toLowerCase() + ":" + m.content());
        }
        return String.join(" | ", parts);
    }
}
