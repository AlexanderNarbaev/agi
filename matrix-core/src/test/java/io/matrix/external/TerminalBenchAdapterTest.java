package io.matrix.external;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W741 — TerminalBench Adapter Tests.
 */
class TerminalBenchAdapterTest {

    @Test
    void testCreateAdapter() {
        TerminalBenchAdapter adapter = new TerminalBenchAdapter(42L);
        assertNotNull(adapter);
        assertTrue(adapter.getTasks().isEmpty());
        assertTrue(adapter.getResults().isEmpty());
    }

    @Test
    void testAddTask() {
        TerminalBenchAdapter adapter = new TerminalBenchAdapter(42L);
        adapter.addTask(new TerminalBenchAdapter.Task(
            "test-1", "Test task",
            TerminalBenchAdapter.TaskType.REASONING, "result", 5
        ));
        assertEquals(1, adapter.getTasks().size());
    }

    @Test
    void testLoadSampleTasks() {
        TerminalBenchAdapter adapter = new TerminalBenchAdapter(42L);
        adapter.loadSampleTasks();
        assertEquals(10, adapter.getTasks().size());
    }

    @Test
    void testRunSingleTask() {
        TerminalBenchAdapter adapter = new TerminalBenchAdapter(42L);
        TerminalBenchAdapter.Task task = new TerminalBenchAdapter.Task(
            "test-1", "List all files in /tmp directory",
            TerminalBenchAdapter.TaskType.SHELL_COMMAND, "file1.txt file2.txt", 10
        );
        adapter.addTask(task);

        TerminalBenchAdapter.TaskResult result = adapter.runTask(task);
        assertNotNull(result);
        assertEquals("test-1", result.taskId());
        assertTrue(result.timeElapsedMs() >= 0);
        assertTrue(result.stepsTaken() > 0);
    }

    @Test
    void testRunAllTasks() {
        TerminalBenchAdapter adapter = new TerminalBenchAdapter(42L);
        adapter.loadSampleTasks();

        TerminalBenchAdapter.BenchmarkSummary summary = adapter.runAll();

        assertEquals(10, summary.totalTasks());
        assertTrue(summary.accuracy() >= 0 && summary.accuracy() <= 1);
        assertTrue(summary.avgTimeMs() >= 0);
        assertEquals(4, summary.accuracyByType().size()); // 4 task types
    }

    @Test
    void testBenchmarkAccuracy() {
        TerminalBenchAdapter adapter = new TerminalBenchAdapter(42L);
        adapter.loadSampleTasks();

        TerminalBenchAdapter.BenchmarkSummary summary = adapter.runAll();

        // Target: >= 60% accuracy (competitive with small SOTA)
        assertTrue(summary.accuracy() >= 0.6,
            "TerminalBench accuracy should be >= 60%, got: " + summary.accuracy());
    }

    @Test
    void testTaskTypes() {
        TerminalBenchAdapter.Task t1 = new TerminalBenchAdapter.Task(
            "1", "shell", TerminalBenchAdapter.TaskType.SHELL_COMMAND, "ok", 5);
        TerminalBenchAdapter.Task t2 = new TerminalBenchAdapter.Task(
            "2", "file", TerminalBenchAdapter.TaskType.FILE_OPERATION, "ok", 5);
        TerminalBenchAdapter.Task t3 = new TerminalBenchAdapter.Task(
            "3", "reason", TerminalBenchAdapter.TaskType.REASONING, "ok", 5);
        TerminalBenchAdapter.Task t4 = new TerminalBenchAdapter.Task(
            "4", "code", TerminalBenchAdapter.TaskType.CODE_GENERATION, "ok", 5);

        assertNotEquals(t1.type(), t2.type());
        assertNotEquals(t2.type(), t3.type());
        assertNotEquals(t3.type(), t4.type());
    }

    @Test
    void testResultsRecorded() {
        TerminalBenchAdapter adapter = new TerminalBenchAdapter(42L);
        adapter.loadSampleTasks();
        adapter.runAll();

        assertEquals(10, adapter.getResults().size());
        for (TerminalBenchAdapter.TaskResult result : adapter.getResults()) {
            assertNotNull(result.taskId());
            assertNotNull(result.actualOutput());
            assertTrue(result.timeElapsedMs() >= 0);
        }
    }

    @Test
    void testNoLLMDependency() {
        // Verify that running tasks does NOT load any LLM
        TerminalBenchAdapter adapter = new TerminalBenchAdapter(42L);
        adapter.loadSampleTasks();

        // Run multiple times — should be fast and not load any model
        long start = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            adapter.runAll();
        }
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 5000, "Running 30 tasks should be fast (no LLM): " + elapsed + "ms");
    }

    @Test
    void testAccuracyByType() {
        TerminalBenchAdapter adapter = new TerminalBenchAdapter(42L);
        adapter.loadSampleTasks();
        TerminalBenchAdapter.BenchmarkSummary summary = adapter.runAll();

        for (var entry : summary.accuracyByType().entrySet()) {
            assertTrue(entry.getValue() >= 0 && entry.getValue() <= 1,
                entry.getKey() + " accuracy out of range: " + entry.getValue());
        }
    }
}
