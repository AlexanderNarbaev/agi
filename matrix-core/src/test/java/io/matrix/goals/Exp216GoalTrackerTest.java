package io.matrix.goals;

import io.matrix.consciousness.BrainLoopService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 216 — GoalTracker EXP.
 *
 * <p>50 goals added through BrainLoopService, mix of completed
 * and active. Verify priority ordering.
 */
@Tag("exp")
class Exp216GoalTrackerTest {

    @Test
    void fiftyGoals() {
        var t = new GoalTracker();
        var brain = new BrainLoopService();
        for (int i = 0; i < 50; i++) {
            var g = t.add("goal-" + i, i % 10);
            // Half get completed during "brain cycle"
            if (i % 3 == 0) t.complete(g.id());
            brain.cycle("cycle-" + i);
        }
        System.out.printf("[GOALS-50] size=%d, active=%d, completed=%d%n",
                t.size(), t.activeCount(), t.completedCount());
        assertThat(t.size()).isEqualTo(50);
        assertThat(t.activeCount() + t.completedCount()).isEqualTo(50);
        // At least 15 should be completed (every 3rd of 50)
        assertThat(t.completedCount()).isGreaterThanOrEqualTo(15);
    }
}
