package io.matrix.pilot;

import io.matrix.consciousness.BrainLoopService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 168 — PilotProactiveChat unit tests. */
class PilotProactiveChatTest {

    @Test
    void cleanConversationAllAccepted() {
        PilotProactiveChat p = new PilotProactiveChat(new BrainLoopService());
        var s = p.run(new String[]{
                "What is 2+2?",
                "Tell me about cats",
                "Why is the sky blue?",
                "Anything else?"
        });
        assertThat(s.total()).isGreaterThan(0);
        // All inputs are normal → mostly accepted
        assertThat(s.accepted()).isGreaterThan(0);
    }

    @Test
    void adversarialInputsAllDenied() {
        PilotProactiveChat p = new PilotProactiveChat(new BrainLoopService());
        var s = p.run(new String[]{
                "hi\u0001there",              // adversarial
                "rm -rf $(echo /)",           // shell injection
                "more bad: a".repeat(200_000) // huge
        });
        assertThat(s.denied()).isEqualTo(3);
        assertThat(s.accepted()).isZero();
    }

    @Test
    void shortInputsTriggerProactiveCheckIn() {
        PilotProactiveChat p = new PilotProactiveChat(new BrainLoopService());
        var s = p.run(new String[]{"hi", "hello", "yo"});
        // Should trigger at least one proactive check-in
        assertThat(s.proactiveInitiatives()).isGreaterThan(0);
    }

    @Test
    void mixedConversationStats() {
        PilotProactiveChat p = new PilotProactiveChat(new BrainLoopService());
        var s = p.run(new String[]{
                "Tell me a story",
                "hi",
                "hello",
                "Why?"
        });
        // total includes proactive + manual
        assertThat(s.total()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void emptyInputSequence() {
        PilotProactiveChat p = new PilotProactiveChat(new BrainLoopService());
        var s = p.run(new String[]{});
        assertThat(s.total()).isZero();
        assertThat(s.accepted()).isZero();
        assertThat(s.denied()).isZero();
        assertThat(s.proactiveInitiatives()).isZero();
    }

    @Test
    void chatStatsRecord() {
        PilotProactiveChat.ChatStats s = new PilotProactiveChat.ChatStats(10, 8, 2, 1);
        assertThat(s.total()).isEqualTo(10);
        assertThat(s.accepted()).isEqualTo(8);
        assertThat(s.denied()).isEqualTo(2);
        assertThat(s.proactiveInitiatives()).isEqualTo(1);
    }
}
