package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W583 — Tests for Matrix Benchmark Suite.
 */
class MatrixBenchmarkSuiteTest {

    @Test
    void testRunBenchmark() {
        MatrixBenchmarkSuite suite = new MatrixBenchmarkSuite();
        suite.addCase(new MatrixBenchmarkSuite.BenchmarkCase(
                "test-1", "Simple test", "What is 2+2?", "4", "math"));
        suite.addCase(new MatrixBenchmarkSuite.BenchmarkCase(
                "test-2", "Another test", "What is 3+3?", "6", "math"));

        // Simple model that returns "4" for all inputs
        MatrixBenchmarkSuite.BenchmarkModel model = input ->
                new MatrixBenchmarkSuite.ModelOutput("4", 0.9);

        MatrixBenchmarkSuite.BenchmarkSummary summary = suite.run("TestModel", model);

        assertEquals(2, summary.totalCases());
        assertEquals(1, summary.correctCases()); // Only first matches
        assertEquals(0.5, summary.accuracy(), 0.01);
    }

    @Test
    void testDomainAccuracy() {
        MatrixBenchmarkSuite suite = new MatrixBenchmarkSuite();
        suite.addCase(new MatrixBenchmarkSuite.BenchmarkCase(
                "logic-1", "Logic test", "A implies B", "B", "logic"));
        suite.addCase(new MatrixBenchmarkSuite.BenchmarkCase(
                "math-1", "Math test", "2+2", "4", "math"));

        MatrixBenchmarkSuite.BenchmarkModel model = input ->
                new MatrixBenchmarkSuite.ModelOutput("4", 0.9);

        MatrixBenchmarkSuite.BenchmarkSummary summary = suite.run("TestModel", model);

        assertTrue(summary.domainAccuracy().containsKey("logic"));
        assertTrue(summary.domainAccuracy().containsKey("math"));
    }

    @Test
    void testCreateStandard() {
        MatrixBenchmarkSuite suite = MatrixBenchmarkSuite.createStandard();

        assertTrue(suite.getCaseCount() > 0);
        assertEquals(7, suite.getCaseCount()); // 3 logic + 2 math + 2 science
    }

    @Test
    void testLatencyTracking() {
        MatrixBenchmarkSuite suite = new MatrixBenchmarkSuite();
        suite.addCase(new MatrixBenchmarkSuite.BenchmarkCase(
                "test-1", "Test", "input", "output", "test"));

        MatrixBenchmarkSuite.BenchmarkModel model = input -> {
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            return new MatrixBenchmarkSuite.ModelOutput("output", 0.9);
        };

        MatrixBenchmarkSuite.BenchmarkSummary summary = suite.run("TestModel", model);

        assertTrue(summary.avgLatencyMs() >= 10);
        assertTrue(summary.totalDurationMs() >= 10);
    }
}
