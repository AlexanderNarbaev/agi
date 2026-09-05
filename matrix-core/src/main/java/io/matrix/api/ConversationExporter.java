package io.matrix.api;

import java.util.List;

/**
 * RUN 130 — Conversation exporter.
 *
 * <p>Serializes a chat history (list of messages) to various formats:
 * plain text, JSON, markdown.
 */
public final class ConversationExporter {

    private ConversationExporter() {}

    /** Plain text format: "USER: hello\nASSISTANT: hi\n..." */
    public static String toText(List<? extends ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (var m : messages) {
            sb.append(m.role().toUpperCase()).append(": ").append(m.content())
                    .append("\n");
        }
        return sb.toString();
    }

    /** Markdown format with headers. */
    public static String toMarkdown(List<? extends ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Conversation Transcript\n\n");
        for (var m : messages) {
            sb.append("**").append(m.role()).append("**: ")
                    .append(m.content()).append("\n\n");
        }
        return sb.toString();
    }

    /** Simple JSON format. */
    public static String toJson(List<? extends ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < messages.size(); i++) {
            var m = messages.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"role\":\"").append(m.role()).append("\",");
            sb.append("\"content\":\"")
                    .append(m.content().replace("\\", "\\\\")
                            .replace("\"", "\\\"").replace("\n", "\\n"))
                    .append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    /** Plain message interface for input. */
    public interface ChatMessage {
        String role();
        String content();
    }
}
