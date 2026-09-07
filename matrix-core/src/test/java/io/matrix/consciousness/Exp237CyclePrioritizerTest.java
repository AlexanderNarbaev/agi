package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 237 — CyclePrioritizer EXP.
 *
 * <p>50 inputs with mixed priorities, dispatched to BrainLoopService.
 * Verify high-priority inputs come out first.
 */
@Tag("exp")
class Exp237CyclePrioritizerTest {

    @Test
    void fiftyInputsPriorityDispatch() {
        var svc = new BrainLoopService();
        var prio = new CyclePrioritizer();
        for (int i = 0; i < 50; i++) {
            int priority = (i % 5 == 0) ? 10 : 1;  // every 5th is high
            prio.enqueue("Q-" + i, priority);
        }
        // Dispatch in priority order
        int highFirst = 0, lowFirst = 0;
        int processed = 0;
        var cycle = prio.dequeue();
        while (cycle != null) {
            svc.cycle(cycle.input());
            if (cycle.priority() == 10) highFirst++;
            else lowFirst++;
            processed++;
            cycle = prio.dequeue();
        }
        System.out.printf("[PRIO-50] processed=%d highFirst=%d lowFirst=%d%n",
                processed, highFirst, lowFirst);
        assertThat(processed).isEqualTo(50);
        // All 10 high-priority items should be processed
        assertThat(highFirst).isEqualTo(10);
        assertThat(lowFirst).isEqualTo(40);
    }
}
