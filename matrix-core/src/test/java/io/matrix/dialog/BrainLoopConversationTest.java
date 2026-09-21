package io.matrix.dialog;

import io.matrix.consciousness.BrainLoopService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 195 — BrainLoop + ConversationRecorder integration. */
class BrainLoopConversationTest {

    @Test
    void conversationStoresAcceptedCycles() {
        BrainLoopService brain = new BrainLoopService();
        var conv = new ConversationRecorder();
        String[] user_inputs = {
                "Hello, how are you?",
                "Tell me about cats",
                "Why is the sky blue?"
        };
        for (String q : user_inputs) {
            var r = brain.cycle(q);
            conv.record("user", q);
            if (r.accepted()) {
                conv.record("assistant", r.action());
            }
        }
        assertThat(conv.turnCount()).isEqualTo(6);
        assertThat(conv.snapshot().turns().get(0).role()).isEqualTo("user");
        assertThat(conv.snapshot().turns().get(5).role()).isEqualTo("assistant");
    }

    @Test
    void adversarialTurnsNotRecorded() {
        BrainLoopService brain = new BrainLoopService();
        var conv = new ConversationRecorder();
        String[] inputs = {
                "Hello",
                "hi\u0001there",     // adversarial, denied
                "How are you?"
        };
        for (String q : inputs) {
            var r = brain.cycle(q);
            conv.record("user", q);
            if (r.accepted()) conv.record("assistant", r.action());
        }
        // 3 user + 2 assistant = 5
        assertThat(conv.turnCount()).isEqualTo(5);
    }

    @Test
    void emptyConversationIsEmpty() {
        var conv = new ConversationRecorder();
        assertThat(conv.recent(5)).isEmpty();
    }
}
