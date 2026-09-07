package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 235 — CycleTickScheduler EXP.
 *
 * <p>50 inputs through 3 ticks (max 20 per tick). Verify scheduler
 * correctly batches inputs.
 */
@Tag("exp")
class Exp235TickSchedulerTest {

    @Test
    void fiftyInputsThroughThreeTicks() {
        var svc = new BrainLoopService();
        var sched = new CycleTickScheduler(20);
        int totalDispatched = 0;
        for (int round = 0; round < 3; round++) {
            var t = sched.beginTick();
            for (int i = 0; i < 20; i++) {
                String input = "tick" + round + "-input" + i;
                var r = sched.dispatch(svc, t, input);
                if (r != null) totalDispatched++;
            }
        }
        System.out.printf("[TICK-EXP] ticks=%d dispatched=%d%n",
                sched.tickCount(), totalDispatched);
        assertThat(sched.tickCount()).isEqualTo(3);
        assertThat(totalDispatched).isEqualTo(60);
    }
}
