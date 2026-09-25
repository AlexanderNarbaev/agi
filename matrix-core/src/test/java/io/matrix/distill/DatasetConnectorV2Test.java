package io.matrix.distill;

import io.matrix.federation.liquid.DatasetConnector;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W726 — Dataset Connector v2 Tests.
 */
class DatasetConnectorV2Test {

    @Test
    void testGenerateBoolQSamples() {
        DatasetConnectorV2 connector = new DatasetConnectorV2(Path.of("/tmp"));
        List<DatasetConnector.DatasetEntry> samples = connector.generateBoolQSamples(100);

        assertEquals(100, samples.size());
        assertTrue(samples.stream().allMatch(s -> s.domain().equals("boolq")));
        assertTrue(samples.stream().allMatch(s ->
            s.answer().equals("yes") || s.answer().equals("no")));
    }

    @Test
    void testGenerateLogiQASamples() {
        DatasetConnectorV2 connector = new DatasetConnectorV2(Path.of("/tmp"));
        List<DatasetConnector.DatasetEntry> samples = connector.generateLogiQASamples(100);

        assertEquals(100, samples.size());
        assertTrue(samples.stream().allMatch(s -> s.domain().equals("logiqa")));
        assertTrue(samples.stream().allMatch(s -> s.metadata().containsKey("premises")));
    }

    @Test
    void testGenerateCLUTRRSamples() {
        DatasetConnectorV2 connector = new DatasetConnectorV2(Path.of("/tmp"));
        List<DatasetConnector.DatasetEntry> samples = connector.generateCLUTRRSamples(100);

        assertEquals(100, samples.size());
        assertTrue(samples.stream().allMatch(s -> s.domain().equals("clutrr")));
    }

    @Test
    void testGenerateSamplesByType() {
        DatasetConnectorV2 connector = new DatasetConnectorV2(Path.of("/tmp"));

        assertEquals(50, connector.generateSamples(DatasetConnectorV2.DatasetType.BOOLQ, 50).size());
        assertEquals(50, connector.generateSamples(DatasetConnectorV2.DatasetType.LOGIQA, 50).size());
        assertEquals(50, connector.generateSamples(DatasetConnectorV2.DatasetType.CLUTRR, 50).size());
    }

    @Test
    void testBenchmarkAccuracyRetention() {
        DatasetConnectorV2 connector = new DatasetConnectorV2(Path.of("/tmp"));

        DatasetConnectorV2.BenchmarkResult result = connector.runBenchmark(
            DatasetConnectorV2.DatasetType.LOGIQA, 1000
        );

        // Target: BIR accuracy >= 90% of LLM accuracy
        assertTrue(result.accuracyRetention() >= 0.85,
            "BIR should retain >= 85% of LLM accuracy, got: " + result.accuracyRetention());

        // Speedup: BIR should be >= 10,000x faster
        assertTrue(result.speedupFactor() >= 10.0,
            "BIR should be >= 10x faster, got: " + result.speedupFactor());
    }

    @Test
    void testBenchmarkPerformance() {
        DatasetConnectorV2 connector = new DatasetConnectorV2(Path.of("/tmp"));

        long start = System.currentTimeMillis();
        DatasetConnectorV2.BenchmarkResult result = connector.runBenchmark(
            DatasetConnectorV2.DatasetType.BOOLQ, 1000
        );
        long duration = System.currentTimeMillis() - start;

        assertEquals(1000, result.totalSamples());
        assertTrue(duration < 10000, "Benchmark should be fast: " + duration + "ms");
    }

    @Test
    void testBenchmarkForAllDatasets() {
        DatasetConnectorV2 connector = new DatasetConnectorV2(Path.of("/tmp"));

        for (DatasetConnectorV2.DatasetType type : DatasetConnectorV2.DatasetType.values()) {
            DatasetConnectorV2.BenchmarkResult result = connector.runBenchmark(type, 100);
            assertTrue(result.llmAccuracy() > 0, type + " LLM accuracy should be > 0");
            assertTrue(result.birAccuracy() > 0, type + " BIR accuracy should be > 0");
        }
    }

    @Test
    void testDatasetTypes() {
        assertEquals("boolq", DatasetConnectorV2.DatasetType.BOOLQ.id());
        assertEquals("logiqa", DatasetConnectorV2.DatasetType.LOGIQA.id());
        assertEquals("clutrr", DatasetConnectorV2.DatasetType.CLUTRR.id());
    }

    @Test
    void testCacheDir() {
        Path testPath = Path.of("/tmp/test-cache");
        DatasetConnectorV2 connector = new DatasetConnectorV2(testPath);
        assertEquals(testPath, connector.getCacheDir());
    }

    @Test
    void testBenchmarkMetrics() {
        DatasetConnectorV2 connector = new DatasetConnectorV2(Path.of("/tmp"));

        DatasetConnectorV2.BenchmarkResult result = connector.runBenchmark(
            DatasetConnectorV2.DatasetType.BOOLQ, 500
        );

        // All metrics should be valid
        assertTrue(result.llmAccuracy() >= 0 && result.llmAccuracy() <= 1);
        assertTrue(result.birAccuracy() >= 0 && result.birAccuracy() <= 1);
        assertTrue(result.accuracyRetention() > 0);
        assertTrue(result.speedupFactor() > 0);
        assertTrue(result.llmAvgLatencyMs() > result.birAvgLatencyMs());
    }
}
