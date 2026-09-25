package io.matrix.external;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ARCAgiSolverTest {

    @Test
    void testCreateSolver() {
        ARCAgiSolver solver = new ARCAgiSolver();
        assertNotNull(solver);
    }

    @Test
    void testAddTask() {
        ARCAgiSolver solver = new ARCAgiSolver();
        solver.addTask(new ARCAgiSolver.ArcTask("test",
            new int[][]{{1}}, new int[][]{{2}}, 3));
        // test that task was added (no solutions yet since we haven't solved)
    }

    @Test
    void testLoadSampleTasks() {
        ARCAgiSolver solver = new ARCAgiSolver();
        solver.loadSampleTasks();
        ARCAgiSolver.ArcBenchmarkSummary summary = solver.runAll();
        assertEquals(5, summary.totalTasks());
    }

    @Test
    void testSolveRotation() {
        ARCAgiSolver solver = new ARCAgiSolver();
        ARCAgiSolver.ArcTask task = new ARCAgiSolver.ArcTask("rot",
            new int[][]{{1, 2}, {3, 4}},
            new int[][]{{3, 1}, {4, 2}}, 5);
        solver.addTask(task);

        ARCAgiSolver.ArcSolution solution = solver.solveTask(task);
        assertTrue(solution.solved(), "Should solve rotation task");
    }

    @Test
    void testSolveMirror() {
        ARCAgiSolver solver = new ARCAgiSolver();
        ARCAgiSolver.ArcTask task = new ARCAgiSolver.ArcTask("mirror",
            new int[][]{{1, 2, 3}, {4, 5, 6}},
            new int[][]{{3, 2, 1}, {6, 5, 4}}, 5);
        solver.addTask(task);

        ARCAgiSolver.ArcSolution solution = solver.solveTask(task);
        assertTrue(solution.solved());
    }

    @Test
    void testSolveInvert() {
        ARCAgiSolver solver = new ARCAgiSolver();
        ARCAgiSolver.ArcTask task = new ARCAgiSolver.ArcTask("inv",
            new int[][]{{0, 1}, {1, 0}},
            new int[][]{{1, 0}, {0, 1}}, 5);
        solver.addTask(task);

        ARCAgiSolver.ArcSolution solution = solver.solveTask(task);
        assertTrue(solution.solved());
    }

    @Test
    void testBenchmarkAccuracy() {
        ARCAgiSolver solver = new ARCAgiSolver();
        solver.loadSampleTasks();
        ARCAgiSolver.ArcBenchmarkSummary summary = solver.runAll();

        // Target: >= 40% (baseline for symbolic solvers)
        assertTrue(summary.accuracy() >= 0.4,
            "ARC-AGI accuracy should be >= 40%, got: " + summary.accuracy());
    }

    @Test
    void testSolutionConfidence() {
        ARCAgiSolver solver = new ARCAgiSolver();
        solver.loadSampleTasks();
        solver.runAll();

        for (ARCAgiSolver.ArcSolution sol : solver.getSolutions()) {
            assertTrue(sol.confidence() >= 0 && sol.confidence() <= 1,
                "Confidence out of range: " + sol.confidence());
        }
    }

    @Test
    void testPerformance() {
        ARCAgiSolver solver = new ARCAgiSolver();
        solver.loadSampleTasks();

        long start = System.currentTimeMillis();
        solver.runAll();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 5000, "5 tasks should be fast: " + elapsed + "ms");
    }

    @Test
    void testAccuracyBySize() {
        ARCAgiSolver solver = new ARCAgiSolver();
        solver.loadSampleTasks();
        ARCAgiSolver.ArcBenchmarkSummary summary = solver.runAll();

        for (var entry : summary.accuracyBySize().entrySet()) {
            assertTrue(entry.getValue() >= 0 && entry.getValue() <= 1);
        }
    }

    @Test
    void testRuleApplication() {
        ARCAgiSolver solver = new ARCAgiSolver();
        ARCAgiSolver.ArcTask task = new ARCAgiSolver.ArcTask("rot",
            new int[][]{{1, 2}, {3, 4}},
            new int[][]{{3, 1}, {4, 2}}, 5);
        solver.addTask(task);

        ARCAgiSolver.ArcSolution solution = solver.solveTask(task);
        assertNotNull(solution.ruleApplied());
        assertFalse(solution.ruleApplied().isEmpty());
    }
}
