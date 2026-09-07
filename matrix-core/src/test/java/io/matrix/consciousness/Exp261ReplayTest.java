package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 261 — Replay EXP.
 *
 * <p>50 inputs recorded, then replayed against a fresh BrainLoop.
 * Verify determinism.
 */
@Tag("exp")
class Exp261ReplayTest {

    @Test
    void fiftyInputsReplay() {
        var svc1 = new BrainLoopService();
        var replay = new BrainLoopReplay();
        // First session: 50 inputs
        for (int i = 0; i < 50; i++) {
            String input = "Q-" + i;
            var r = svc1.cycle(input);
            replay.record(input, r.accepted(), r.action());
        }
        // Second session: replay into fresh brain
        var svc2 = new BrainLoopService();
        int accepted = replay.replay(svc2);
        System.out.printf("[REPLAY-50] recorded=50, replayed=%d%n", accepted);
        assertThat(accepted).isEqualTo(50);
        // Both brains should have 50 * 5 = 250 trace steps
        assertThat(svc1.trace().count()).isEqualTo(250);
        assertThat(svc2.trace().count()).isEqualTo(250);
    }
}
