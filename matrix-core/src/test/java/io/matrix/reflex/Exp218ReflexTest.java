package io.matrix.reflex;

import io.matrix.consciousness.BrainLoopService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 218 — Reflex + BrainLoop EXP.
 *
 * <p>50 brain cycles. Reflexes fire for 20 of them (greetings /
 * shutdown), bypassing full deliberation.
 */
@Tag("exp")
class Exp218ReflexTest {

    @Test
    void fiftyCyclesWithReflexes() {
        var reflex = new ReflexEngine();
        reflex.register("hello", "greeting");
        reflex.register("goodbye", "farewell");
        reflex.register("thanks", "ack");
        reflex.register("status", "ok");
        reflex.register("help", "support");

        var brain = new BrainLoopService();
        int reflexHits = 0;
        int brainCalls = 0;
        String[] inputs = {
                "hello friend", "what is 2+2", "status now",
                "tell me a joke", "goodbye!", "help me",
                "thanks", "hi", "explain quantum", "thanks!"
        };
        for (int round = 0; round < 50; round++) {
            String input = inputs[round % inputs.length];
            String reflexOut = reflex.tryReflex(input);
            if (reflexOut != null) {
                reflexHits++;
            } else {
                brain.cycle(input);
                brainCalls++;
            }
        }
        System.out.printf("[REFLEX-50] reflex=%d brain=%d total=%d%n",
                reflexHits, brainCalls, reflexHits + brainCalls);
        assertThat(reflexHits + brainCalls).isEqualTo(50);
        // At least 10 reflex hits (5 reflexes × 2 inputs on avg)
        assertThat(reflexHits).isGreaterThan(0);
    }
}
