package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 130 — ConversationExporter unit tests. */
class ConversationExporterTest {

    @Test
    void textFormatBasic() {
        String text = ConversationExporter.toText(List.of(
                new TestMessage("user", "hi"),
                new TestMessage("assistant", "hello")));
        assertThat(text).contains("USER: hi");
        assertThat(text).contains("ASSISTANT: hello");
    }

    @Test
    void markdownFormat() {
        String md = ConversationExporter.toMarkdown(List.of(
                new TestMessage("user", "hi")));
        assertThat(md).contains("# Conversation Transcript");
        assertThat(md).contains("**user**: hi");
    }

    @Test
    void jsonFormat() {
        String json = ConversationExporter.toJson(List.of(
                new TestMessage("user", "hi"),
                new TestMessage("assistant", "hello world")));
        assertThat(json).startsWith("[");
        assertThat(json).endsWith("]");
        assertThat(json).contains("\"role\":\"user\"");
        assertThat(json).contains("\"content\":\"hi\"");
        assertThat(json).contains("\"hello world\"");
    }

    @Test
    void jsonEscapesQuotes() {
        String json = ConversationExporter.toJson(List.of(
                new TestMessage("user", "He said \"hi\"")));
        assertThat(json).contains("\\\"");
    }

    @Test
    void jsonEscapesNewlines() {
        String json = ConversationExporter.toJson(List.of(
                new TestMessage("user", "line1\nline2")));
        assertThat(json).contains("\\n");
    }

    @Test
    void emptyList() {
        assertThat(ConversationExporter.toText(List.of())).isEmpty();
        assertThat(ConversationExporter.toMarkdown(List.of())).contains("# Conversation Transcript");
        assertThat(ConversationExporter.toJson(List.of())).isEqualTo("[]");
    }

    @Test
    void singleMessage() {
        String text = ConversationExporter.toText(List.of(
                new TestMessage("system", "be brief")));
        assertThat(text).contains("SYSTEM: be brief");
    }

    private record TestMessage(String role, String content)
            implements ConversationExporter.ChatMessage {}
}
