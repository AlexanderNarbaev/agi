package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 243 — Checkpoints EXP.
 *
 * <p>50 cycles, 5 named checkpoints, verify trace counts.
 */
@Tag("exp")
class Exp243CheckpointsTest {

    @Test
    void fiftyCyclesWithFiveCheckpoints() {
        var svc = new BrainLoopService();
        var cks = new BrainLoopCheckpoints();
        cks.create(svc, "start");
        for (int round = 0; round < 5; round++) {
            for (int i = 0; i < 10; i++) {
                svc.cycle("Q-" + round + "-" + i);
            }
            cks.create(svc, "after-round-" + round);
        }
        var list = cks.list();
        System.out.printf("[CKPT-EXP] size=%d, last=after-round-4%n", list.size());
        assertThat(list).hasSize(6);  // 1 start + 5 after-round
        var last = list.get(5);
        assertThat(last.traceCount()).isEqualTo(50 * 5);  // 5 rounds × 10 cycles × 5 steps/cycle
        assertThat(last.tag()).isEqualTo("after-round-4");
    }
}
