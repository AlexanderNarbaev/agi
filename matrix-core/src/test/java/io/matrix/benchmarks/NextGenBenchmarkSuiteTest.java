package io.matrix.benchmarks;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NextGenBenchmarkSuiteTest {

    @Test
    void testCreateSuite() {
        NextGenBenchmarkSuite suite = new NextGenBenchmarkSuite();
        assertNotNull(suite);
        assertTrue(suite.getResults().isEmpty());
    }

    @Test
    void testRunAll() {
        NextGenBenchmarkSuite suite = new NextGenBenchmarkSuite();
        List<NextGenBenchmarkSuite.BenchmarkResult> results = suite.runAll();
        assertEquals(5, results.size());
        for (var r : results) {
            assertTrue(r.totalTasks() > 0);
            assertTrue(r.accuracy() >= 0 && r.accuracy() <= 1);
        }
    }

    @Test
    void testSWEBenchResult() {
        NextGenBenchmarkSuite suite = new NextGenBenchmarkSuite();
        suite.runAll();
        var swe = suite.getResults().get(NextGenBenchmarkSuite.BenchmarkType.SWE_BENCH);
        assertNotNull(swe);
        assertEquals(50, swe.totalTasks());
    }

    @Test
    void testMMMUBenchResult() {
        NextGenBenchmarkSuite suite = new NextGenBenchmarkSuite();
        suite.runAll();
        var mmmu = suite.getResults().get(NextGenBenchmarkSuite.BenchmarkType.MMMU);
        assertNotNull(mmmu);
        assertEquals(100, mmmu.totalTasks());
    }

    @Test
    void testAgentBenchResult() {
        NextGenBenchmarkSuite suite = new NextGenBenchmarkSuite();
        suite.runAll();
        var agent = suite.getResults().get(NextGenBenchmarkSuite.BenchmarkType.AGENT_BENCH);
        assertNotNull(agent);
        // MATRIX MCP advantage: >= 70% accuracy
        assertTrue(agent.accuracy() >= 0.70, "AgentBench should be >= 70%: " + agent.accuracy());
    }

    @Test
    void testMineBenchResult() {
        NextGenBenchmarkSuite suite = new NextGenBenchmarkSuite();
        suite.runAll();
        var mine = suite.getResults().get(NextGenBenchmarkSuite.BenchmarkType.MINE_BENCH);
        assertNotNull(mine);
        assertTrue(mine.accuracy() >= 0.85, "MineBench should be >= 85%: " + mine.accuracy());
    }

    @Test
    void testHardwareBenchResult() {
        NextGenBenchmarkSuite suite = new NextGenBenchmarkSuite();
        suite.runAll();
        var hw = suite.getResults().get(NextGenBenchmarkSuite.BenchmarkType.HARDWARE_BENCH);
        assertNotNull(hw);
        assertTrue(hw.accuracy() >= 0.80, "HardwareBench should be >= 80%: " + hw.accuracy());
    }

    @Test
    void testReportGeneration() {
        NextGenBenchmarkSuite suite = new NextGenBenchmarkSuite();
        suite.runAll();
        String report = suite.generateReport();
        assertNotNull(report);
        assertTrue(report.contains("MATRIX Omni-Modal Benchmark Report"));
        assertTrue(report.contains("SWE-bench"));
        assertTrue(report.contains("MMMU"));
    }

    @Test
    void testResultsAreOrdered() {
        NextGenBenchmarkSuite suite = new NextGenBenchmarkSuite();
        suite.runAll();
        var results = suite.getResults();
        // LinkedHashMap preserves insertion order
        assertEquals(NextGenBenchmarkSuite.BenchmarkType.SWE_BENCH,
            results.keySet().iterator().next());
    }
}
