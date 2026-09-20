package io.matrix.federation.liquid.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W601 — Benchmark Runner Tests.
 */
class BenchmarkRunnerTest {

    @Test
    void testLogicBenchmark() {
        LogicBenchmark bench = new LogicBenchmark();
        bench.addPuzzles(LogicBenchmark.generateSyntheticPuzzles(100));

        var result = bench.run("Test", LogicBenchmark.birSolver());
        assertNotNull(result);
        assertTrue(result.totalPuzzles() > 0);
        assertTrue(result.accuracy() >= 0);
        assertTrue(result.avgLatencyMs() >= 0);
    }

    @Test
    void testSampleEfficiency() {
        var examples = SampleEfficiencyBenchmark.generateClassificationExamples(100);
        var train = examples.subList(0, 80);
        var test = examples.subList(80, 100);

        SampleEfficiencyBenchmark bench = new SampleEfficiencyBenchmark(train, test);
        int[] counts = {1, 10, 50, 80};
        var result = bench.run("HDC", SampleEfficiencyBenchmark.hdcLearner(), counts);

        assertNotNull(result);
        assertTrue(result.curve().size() > 0);
        assertTrue(result.aucScore() >= 0);
    }

    @Test
    void testCausalityBenchmark() {
        CausalityBenchmark bench = new CausalityBenchmark();
        var result = bench.run("BIR", CausalityBenchmark.birCausalSolver());

        assertNotNull(result);
        assertTrue(result.totalScenarios() > 0);
        assertTrue(result.accuracy() >= 0);
    }

    @Test
    void testEnergyBenchmark() {
        EnergyBenchmark bench = new EnergyBenchmark();
        var result = bench.measure("Test", () -> Math.sqrt(42), 1000, "CPU");

        assertNotNull(result);
        assertTrue(result.opsPerSecond() >= 0);
        assertTrue(result.estimatedJoulesPerOp() >= 0);
    }

    @Test
    void testFullBenchmarkRun() {
        BenchmarkRunner runner = new BenchmarkRunner();
        var report = runner.runAll();

        assertNotNull(report);
        assertFalse(report.logicResults.isEmpty());
        assertFalse(report.sampleResults.isEmpty());
        assertFalse(report.causalityResults.isEmpty());
        assertFalse(report.energyResults.isEmpty());

        String markdown = report.toMarkdown();
        assertNotNull(markdown);
        assertTrue(markdown.contains("# MATRIX Benchmark Report"));
    }
}
