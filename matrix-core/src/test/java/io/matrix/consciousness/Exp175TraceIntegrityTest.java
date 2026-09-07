package io.matrix.consciousness;

import io.matrix.auditor.MatrixTrace;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 175 — Trace integrity EXP.
 *
 * <p>Run 500 brain cycles, verify every step in the trace has
 * valid hash chain (prevHash == previous hash). Per CONSTITUTION VIII.
 */
@Tag("exp")
class Exp175TraceIntegrityTest {

    @Test
    void fiveHundredCyclesValidTrace() {
        BrainLoopService svc = new BrainLoopService();
        for (int i = 0; i < 500; i++) {
            svc.cycle("Q-" + i);
        }
        // 500 cycles × 5 trace steps = 2500
        assertThat(svc.trace().count()).isEqualTo(2500);

        // Verify hash chain integrity
        var steps = svc.trace().steps();
        int validLinks = 0;
        for (int i = 1; i < steps.size(); i++) {
            MatrixTrace.TraceStep prev = steps.get(i - 1);
            MatrixTrace.TraceStep curr = steps.get(i);
            if (curr.prevHash.equals(prev.hash)) validLinks++;
        }
        System.out.printf("[TRACE-INTEGRITY-500] %d/%d links valid%n",
                validLinks, steps.size() - 1);
        assertThat(validLinks).isEqualTo(steps.size() - 1);
    }

    @Test
    void crossInstanceDeterministic() {
        // Two services, same inputs → same final hash
        BrainLoopService a = new BrainLoopService();
        BrainLoopService b = new BrainLoopService();
        for (int i = 0; i < 100; i++) {
            a.cycle("cycle-" + i);
            b.cycle("cycle-" + i);
        }
        var aLast = a.trace().last();
        var bLast = b.trace().last();
        System.out.printf("[TRACE-CROSS] a=%s b=%s%n",
                aLast.hash.substring(0, 16), bLast.hash.substring(0, 16));
        assertThat(aLast.hash).isEqualTo(bLast.hash);
    }
}
