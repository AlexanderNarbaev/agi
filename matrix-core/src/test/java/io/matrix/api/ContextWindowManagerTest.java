package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 124 — ContextWindowManager unit tests. */
class ContextWindowManagerTest {

    @Test
    void emptyListReturnsEmpty() {
        ContextWindowManager m = new ContextWindowManager(100);
        assertThat(m.trim(List.of())).isEmpty();
    }

    @Test
    void smallListFits() {
        ContextWindowManager m = new ContextWindowManager(1000);
        var msgs = List.of(
                QwenChatTemplate.Message.system("sys"),
                QwenChatTemplate.Message.user("hi"));
        var trimmed = m.trim(msgs);
        assertThat(trimmed).hasSize(2);
    }

    @Test
    void largeListGetsTrimmed() {
        ContextWindowManager m = new ContextWindowManager(50);
        var msgs = List.of(
                QwenChatTemplate.Message.system("sys"),
                QwenChatTemplate.Message.user("a".repeat(500)),
                QwenChatTemplate.Message.assistant("b".repeat(500)),
                QwenChatTemplate.Message.user("c"));
        var trimmed = m.trim(msgs);
        // Should keep system + last few messages
        assertThat(trimmed.size()).isLessThan(msgs.size());
        assertThat(trimmed.get(0).role()).isEqualTo(QwenChatTemplate.Role.SYSTEM);
    }

    @Test
    void keepsMostRecentMessages() {
        ContextWindowManager m = new ContextWindowManager(80);
        var msgs = List.of(
                QwenChatTemplate.Message.system("sys"),
                QwenChatTemplate.Message.user("a"),
                QwenChatTemplate.Message.user("b"),
                QwenChatTemplate.Message.user("c"),
                QwenChatTemplate.Message.user("d"),
                QwenChatTemplate.Message.user("e"));
        var trimmed = m.trim(msgs);
        // Most recent should be preserved
        assertThat(trimmed.get(trimmed.size() - 1).content()).isEqualTo("e");
    }

    @Test
    void estimateTokensBasic() {
        int t = ContextWindowManager.estimateTokens(
                QwenChatTemplate.Message.user("Hello world"));
        // 11 chars / 4 + 4 markers = 2 + 4 = 6
        assertThat(t).isGreaterThan(0);
    }

    @Test
    void invalidMaxTokensRejected() {
        assertThatThrownBy(() -> new ContextWindowManager(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextWindowManager(-10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accessor() {
        ContextWindowManager m = new ContextWindowManager(2048);
        assertThat(m.maxTokens()).isEqualTo(2048);
    }

    @Test
    void noSystemMessageKeepsAllIfFits() {
        ContextWindowManager m = new ContextWindowManager(1000);
        var msgs = List.of(
                QwenChatTemplate.Message.user("a"),
                QwenChatTemplate.Message.user("b"));
        var trimmed = m.trim(msgs);
        assertThat(trimmed).hasSize(2);
    }

    @Test
    void noSystemMessageTrimsFromFront() {
        ContextWindowManager m = new ContextWindowManager(15);
        var msgs = List.of(
                QwenChatTemplate.Message.user("a".repeat(100)),
                QwenChatTemplate.Message.user("b"),
                QwenChatTemplate.Message.user("c"));
        var trimmed = m.trim(msgs);
        // Budget is too small for the long 'a' message
        assertThat(trimmed).noneMatch(x -> x.content().startsWith("a".repeat(50)));
        // Most recent messages should be kept
        assertThat(trimmed).anyMatch(x -> x.content().equals("c"));
    }
}
