package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 72 — QwenChatTemplate unit tests. */
class QwenChatTemplateTest {

    @Test
    void buildPromptProducesChatmlFormat() {
        String prompt = QwenChatTemplate.buildPrompt(List.of(
                QwenChatTemplate.Message.system("Be helpful."),
                QwenChatTemplate.Message.user("Hi")
        ));
        assertThat(prompt).startsWith("<|im_start|>system\nBe helpful.<|im_end|>\n");
        assertThat(prompt).contains("<|im_start|>user\nHi<|im_end|>\n");
        assertThat(prompt).endsWith("<|im_start|>assistant\n");
    }

    @Test
    void buildUserPromptHasDefaultSystemMessage() {
        String prompt = QwenChatTemplate.buildUserPrompt("What is 2+2?");
        assertThat(prompt).startsWith("<|im_start|>system\n");
        assertThat(prompt).contains("You are a helpful assistant.");
        assertThat(prompt).contains("What is 2+2?");
        assertThat(prompt).endsWith("<|im_start|>assistant\n");
    }

    @Test
    void cleanReplyStripsImEnd() {
        String cleaned = QwenChatTemplate.cleanReply("Hello there<|im_end|>");
        assertThat(cleaned).isEqualTo("Hello there");
    }

    @Test
    void cleanReplyStripsEndOfText() {
        String cleaned = QwenChatTemplate.cleanReply("Bye<|endoftext|>");
        assertThat(cleaned).isEqualTo("Bye");
    }

    @Test
    void cleanReplyStripsAssistantMarker() {
        String cleaned = QwenChatTemplate.cleanReply("<|im_start|>assistant\nHi");
        assertThat(cleaned).isEqualTo("Hi");
    }

    @Test
    void cleanReplyHandlesNull() {
        assertThat(QwenChatTemplate.cleanReply(null)).isNull();
    }

    @Test
    void cleanReplyStripsMultipleMarkers() {
        String cleaned = QwenChatTemplate.cleanReply("Hello<|im_end|><|endoftext|>");
        assertThat(cleaned).isEqualTo("Hello");
    }

    @Test
    void estimateTokensRoughlyCorrect() {
        List<QwenChatTemplate.Message> msgs = List.of(
                QwenChatTemplate.Message.system("Hi"),
                QwenChatTemplate.Message.user("Hello world how are you")
        );
        int tokens = QwenChatTemplate.estimateTokens(msgs);
        // 2 chars/4 + 4 overhead per message
        assertThat(tokens).isGreaterThan(8);
        assertThat(tokens).isLessThan(50);
    }

    @Test
    void specialTokenIdsCorrect() {
        assertThat(QwenChatTemplate.IM_START).isEqualTo(151644);
        assertThat(QwenChatTemplate.IM_END).isEqualTo(151645);
        assertThat(QwenChatTemplate.ENDOFTEXT).isEqualTo(151643);
    }

    @Test
    void messageFactories() {
        assertThat(QwenChatTemplate.Message.system("x").role())
                .isEqualTo(QwenChatTemplate.Role.SYSTEM);
        assertThat(QwenChatTemplate.Message.user("x").role())
                .isEqualTo(QwenChatTemplate.Role.USER);
        assertThat(QwenChatTemplate.Message.assistant("x").role())
                .isEqualTo(QwenChatTemplate.Role.ASSISTANT);
    }

    @Test
    void emptyMessageListProducesAssistantPrompt() {
        String prompt = QwenChatTemplate.buildPrompt(List.of());
        assertThat(prompt).isEqualTo("<|im_start|>assistant\n");
    }

    @Test
    void renderJoinsMessages() {
        String s = QwenChatTemplate.render(List.of(
                QwenChatTemplate.Message.user("Hi"),
                QwenChatTemplate.Message.assistant("Hello")
        ));
        assertThat(s).isEqualTo("user:Hi | assistant:Hello");
    }
}
