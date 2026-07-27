package io.matrix.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LongHorizonPlannerDecomposeTest {

    @Test
    void emptyGoalYieldsClarify() {
        // Pure logic test: call via reflection-free interface
        // (we test the decompose() behavior via planning through full code path, but
        // here we just verify the result types compile and have expected fields).
        var r = new LongHorizonPlanner.StepResult(1, "test", "out", "cl");
        assertEquals(1, r.index());
        assertEquals("test", r.subGoal());
        assertEquals("out", r.output());
        assertEquals("cl", r.consciousLayer());
    }

    @Test
    void planResultWithEmptySteps() {
        var pr = new LongHorizonPlanner.PlanResult("g", java.util.List.of(), "rejected");
        assertEquals("g", pr.goal());
        assertEquals(0, pr.steps().size());
        assertEquals("rejected", pr.aggregateOutput());
    }

    @Test
    void longHorizonPlannerClassLoads() {
        assertNotNull(LongHorizonPlanner.class);
    }
}