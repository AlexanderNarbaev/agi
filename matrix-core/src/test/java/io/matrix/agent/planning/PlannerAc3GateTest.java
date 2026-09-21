package io.matrix.agent.planning;

import io.matrix.agent.ExecutablePlanner;
import io.matrix.agent.PlanStep;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** DESIGN-15 §3: AC-3 gate fast-fails execution on unsatisfiable CSP. */
class PlannerAc3GateTest {

    @Test
    void unsatisfiableCspFastFailsWithoutExecution() {
        var planner = new ExecutablePlanner(); // tools unused on this path
        List<PlanStep> steps = List.of(PlanStep.invokeTool("t", java.util.Map.of(), "step"));
        var alwaysFalse = new Ac3Solver.BinaryConstraint() {
            public boolean consistent(int vi, int a, int vj, int b) { return false; }
            public int vi() { return 0; } public int vj() { return 1; }
        };
        BitSet[] d = new BitSet[2];
        for (int i = 0; i < 2; i++) { d[i] = new BitSet(2); d[i].set(0, 2); }
        var csp = new Ac3Solver(d, 2, List.of(alwaysFalse));

        var result = planner.executeSteps("goal", steps, csp);
        assertThat(result.allPassed()).isFalse();
        assertThat(result.summary()).isEqualTo("unsatisfiable_preconditions");
        assertThat(result.steps()).isEmpty(); // nothing executed
    }

    @Test
    void satisfiableCspPassesThrough() {
        var planner = new ExecutablePlanner();
        List<PlanStep> steps = List.of(PlanStep.invokeTool("t", java.util.Map.of(), "step"));
        BitSet[] d = new BitSet[2];
        for (int i = 0; i < 2; i++) { d[i] = new BitSet(2); d[i].set(0, 2); }
        // trivially satisfiable: identity constraint
        var csp = new Ac3Solver(d, 2, List.of(new Ac3Solver.BinaryConstraint() {
            public boolean consistent(int vi, int a, int vj, int b) { return true; }
            public int vi() { return 0; } public int vj() { return 1; }
        }));
        // Pass-through path needs tools for actual execution; here we only assert
        // that the gate does NOT fast-fail (exception from null tools is expected
        // beyond the gate, so catch anything and verify summary != gate marker).
        try {
            var r = planner.executeSteps("g", steps, csp);
            assertThat(r.summary()).isNotEqualTo("unsatisfiable_preconditions");
        } catch (Exception e) {
            throw new AssertionError("gate must not block satisfiable CSP", e);
        }
    }
}
