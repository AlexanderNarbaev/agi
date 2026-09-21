package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 239 — CycleAggregator EXP.
 *
 * <p>200 cycles through BrainLoop, aggregate in 50-cycle windows.
 */
@Tag("exp")
class Exp239CycleAggregatorTest {

    @Test
    void twoHundredCyclesWindows() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 200; i++) svc.cycle("Q-" + i);
        // Window size 50 → 4 windows, but only one snapshot at the end
        var w = CycleAggregator.aggregate(svc, 50);
        System.out.printf("[AGG-200] %s%n", CycleAggregator.format(w));
        // 200 cycles total but window is capped at 50
        assertThat(w.cycles()).isEqualTo(50);
    }
}
