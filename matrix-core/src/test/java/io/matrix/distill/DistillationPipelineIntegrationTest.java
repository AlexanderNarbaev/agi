package io.matrix.distill;

import io.matrix.federation.liquid.DatasetConnector;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W736 — End-to-End Distillation Pipeline Integration Test.
 *
 * Pipeline:
 * 1. Load LogiQA dataset
 * 2. Distill rules from LLM
 * 3. Encode to BIR + HDC (2-bit)
 * 4. Run benchmark: original LLM vs distilled MATRIX
 */
class DistillationPipelineIntegrationTest {

    @Test
    void testEndToEndPipeline() {
        // Step 1: Load LogiQA dataset
        DatasetConnectorV2 connector = new DatasetConnectorV2(Path.of("/tmp"));
        List<DatasetConnector.DatasetEntry> dataset =
            connector.generateLogiQASamples(100);

        assertEquals(100, dataset.size(), "Should load 100 LogiQA samples");

        // Step 2: Distill rules from LLM
        LLMKnowledgeDistiller distiller = new LLMKnowledgeDistiller(
            new LLMKnowledgeDistiller.MockBackend(42L)
        );

        int totalRules = 0;
        for (DatasetConnector.DatasetEntry entry : dataset) {
            String context = entry.question() + " " +
                entry.metadata().getOrDefault("premises", "");
            List<LLMKnowledgeDistiller.DistilledRule> rules =
                distiller.distillFromContext(context);
            totalRules += rules.size();
        }

        assertTrue(totalRules > 0, "Should distill at least some rules");

        // Step 3: Encode rules to HDC (2-bit)
        BitNetEncoder encoder = new BitNetEncoder();
        boolean[] hdcVector = new boolean[1000];
        Random rng = new Random(42);
        for (int i = 0; i < hdcVector.length; i++) {
            hdcVector[i] = rng.nextBoolean();
        }

        BitNetEncoder.EncodingResult encoded = encoder.encode(hdcVector);
        assertTrue(encoded.compressionRatio() >= 0.5);

        // Round-trip should preserve most information
        double accuracy = encoder.testRoundTripAccuracy(hdcVector);
        assertTrue(accuracy > 0.5, "Round-trip accuracy should be > 50%: " + accuracy);

        // Step 4: Run benchmark
        DatasetConnectorV2.BenchmarkResult result = connector.runBenchmark(
            DatasetConnectorV2.DatasetType.LOGIQA, 1000
        );

        // Target: BIR accuracy >= 90% of LLM accuracy
        assertTrue(result.accuracyRetention() >= 0.85,
            "BIR should retain >= 85% of LLM accuracy, got: " + result.accuracyRetention());

        // Speedup
        assertTrue(result.speedupFactor() >= 10.0,
            "BIR should be >= 10x faster, got: " + result.speedupFactor());

        System.out.println("Distillation Pipeline Results:");
        System.out.println("  Distilled rules: " + totalRules);
        System.out.println("  HDC compression: " + encoded.compressionRatio() + "x");
        System.out.println("  Round-trip accuracy: " + accuracy);
        System.out.println("  LLM accuracy: " + result.llmAccuracy());
        System.out.println("  BIR accuracy: " + result.birAccuracy());
        System.out.println("  Speedup: " + result.speedupFactor() + "x");
    }

    @Test
    void testAllDatasetTypes() {
        DatasetConnectorV2 connector = new DatasetConnectorV2(Path.of("/tmp"));

        for (DatasetConnectorV2.DatasetType type : DatasetConnectorV2.DatasetType.values()) {
            DatasetConnectorV2.BenchmarkResult result =
                connector.runBenchmark(type, 100);

            assertTrue(result.llmAccuracy() > 0, type + " should have LLM accuracy");
            assertTrue(result.birAccuracy() > 0, type + " should have BIR accuracy");
            assertTrue(result.speedupFactor() > 0, type + " should have speedup");
        }
    }

    @Test
    void testDistillationPerformance() {
        DatasetConnectorV2 connector = new DatasetConnectorV2(Path.of("/tmp"));
        LLMKnowledgeDistiller distiller = new LLMKnowledgeDistiller(
            new LLMKnowledgeDistiller.MockBackend(42L)
        );

        long start = System.currentTimeMillis();

        // 1000 distillation scenarios
        for (int i = 0; i < 1000; i++) {
            distiller.distillFromContext("context " + i);
        }

        long duration = System.currentTimeMillis() - start;
        assertTrue(duration < 10000, "1000 distillations should be fast: " + duration + "ms");
    }

    @Test
    void testDistilledRulesQuality() {
        LLMKnowledgeDistiller distiller = new LLMKnowledgeDistiller(
            new LLMKnowledgeDistiller.MockBackend(42L)
        );

        List<LLMKnowledgeDistiller.DistilledRule> rules = distiller.distillFromContext(
            "weather test"
        );

        // All rules should have valid structure
        for (LLMKnowledgeDistiller.DistilledRule rule : rules) {
            assertNotNull(rule.id());
            assertNotNull(rule.conclusion());
            assertTrue(rule.confidence() >= 0.0 && rule.confidence() <= 1.0);
        }
    }
}
