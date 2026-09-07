package io.matrix.dialog;

import io.matrix.consciousness.BrainLoopService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 196 — Conversation EXP.
 *
 * <p>20-turn realistic conversation through BrainLoopService,
 * recorded in ConversationRecorder. Verifies M0 memory behaves.
 */
@Tag("exp")
class Exp196ConversationTest {

    @Test
    void twentyTurnConversation() {
        BrainLoopService brain = new BrainLoopService();
        var conv = new ConversationRecorder().withMaxTurns(40);
        String[] user_inputs = {
                "Hi there",
                "What's the capital of France?",
                "Tell me a joke",
                "Explain quantum entanglement",
                "Why do we sleep?",
                "What's your favorite color?",
                "Tell me about photosynthesis",
                "How does rain form?",
                "What is the speed of light?",
                "Why is the sky blue?",
                "hi\u0001adversarial",   // dropped
                "How do magnets work?",
                "What is DNA?",
                "Explain black holes",
                "Tell me about Mozart",
                "What's 7 times 8?",
                "What is love?",
                "Define consciousness",
                "Who am I?",
                "Goodbye"
        };
        int accepted = 0;
        for (String q : user_inputs) {
            conv.record("user", q);
            var r = brain.cycle(q);
            if (r.accepted()) {
                conv.record("assistant", r.action());
                accepted++;
            }
        }
        // 20 user inputs; 1 adversarial → 19 accepted responses
        int expected = user_inputs.length - 1;
        System.out.printf("[CONV-20] accepted=%d/%d, recorded=%d turns%n",
                accepted, user_inputs.length, conv.turnCount());
        // With adversarial filtered: 19 responses + 19 user = 38
        assertThat(accepted).isEqualTo(expected);
        // 39 (1 adv dropped user still recorded): 19+19+1 = 39
        assertThat(conv.turnCount()).isEqualTo(2 * accepted + 1);
    }
}
