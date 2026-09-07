package io.matrix.consciousness;

import io.matrix.auditor.MatrixTrace;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 160 — BrainLoop EXP.
 *
 * <p>Real workload: 100 cycles with deterministic inputs.
 * Verify trace integrity, determinism, gate behaviour.
 */
@Tag("exp")
class Exp160BrainLoopTest {

    @Test
    void hundredCyclesDeterminism() {
        BrainLoopService svc = new BrainLoopService();
        for (int i = 0; i < 100; i++) {
            svc.cycle("query-" + i);
        }
        System.out.printf("[BRAIN-LOOP-100] trace=%d arousal=%.3f%n",
                svc.trace().count(), svc.arousal());

        // Verify trace chain integrity
        var steps = svc.trace().steps();
        for (int i = 1; i < steps.size(); i++) {
            MatrixTrace.TraceStep prev = steps.get(i - 1);
            MatrixTrace.TraceStep curr = steps.get(i);
            assertThat(curr.prevHash).isEqualTo(prev.hash);
        }
    }

    @Test
    void deterministicAcrossInstances() {
        // Two services, same inputs → same outputs
        BrainLoopService a = new BrainLoopService();
        BrainLoopService b = new BrainLoopService();
        for (int i = 0; i < 50; i++) {
            String q = "Q-" + i;
            var ra = a.cycle(q);
            var rb = b.cycle(q);
            assertThat(ra.action()).isEqualTo(rb.action());
        }
        System.out.println("[BRAIN-DETERM] 50 queries, all identical");
    }

    @Test
    void adversarialInputsAllDeny() {
        BrainLoopService svc = new BrainLoopService();
        String[] attacks = {
                "hi\u0001there",      // ctrl-A
                "rm -rf $(echo /)",  // shell injection
                "rm -rf ".repeat(200_000)  // huge input
        };
        int denied = 0;
        for (String attack : attacks) {
            if (!svc.cycle(attack).accepted()) denied++;
        }
        System.out.printf("[BRAIN-SECURITY] denied=%d/%d%n",
                denied, attacks.length);
        assertThat(denied).isEqualTo(attacks.length);
    }
}
