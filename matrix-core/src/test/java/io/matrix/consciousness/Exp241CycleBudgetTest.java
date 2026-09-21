package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 241 — CycleBudget EXP.
 *
 * <p>Try to consume 100 cycles with a budget of 50. Verify that
 * exactly 50 succeed.
 */
@Tag("exp")
class Exp241CycleBudgetTest {

    @Test
    void hundredCyclesFiftyBudget() {
        var svc = new BrainLoopService();
        var budget = new CycleBudget(50);
        int dispatched = 0;
        int refused = 0;
        for (int i = 0; i < 100; i++) {
            if (budget.tryConsume()) {
                svc.cycle("X-" + i);
                dispatched++;
            } else {
                refused++;
            }
        }
        System.out.printf("[BUDGET-100] dispatched=%d refused=%d%n",
                dispatched, refused);
        assertThat(dispatched).isEqualTo(50);
        assertThat(refused).isEqualTo(50);
        // BrainLoop ran 50 cycles → 250 trace steps
        assertThat(svc.trace().count()).isEqualTo(250);
    }
}
