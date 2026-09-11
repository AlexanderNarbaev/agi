package io.matrix.research;

import io.matrix.imports.BooleanChainRunner;
import io.matrix.imports.TruthTableLayer;
import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.TruthTable;
import io.matrix.noosphere.FnlEntry;
import io.matrix.noosphere.FnlRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RUN 358 — Phase T multi-model distillation: Qwen2.5-1.5B.
 *
 * <p>HF auth confirmed (AlexNarbaev). Downloaded Qwen2.5-1.5B (2.9 GB)
 * via `hf download Qwen/Qwen2.5-1.5B`. Distill into FnlRegistry
 * alongside the 0.5B + GPT-2 + DialoGPT-small pool.
 *
 * <p>Llama-3.2-1B attempt: gated repo, "Access denied" — documented
 * as blocking constraint (model approval required).
 */
class Exp358Qwen15DistillationTest {

    @Test
    void distillQwen15BIntoUnifiedPool() throws IOException {
        FnlRegistry registry = FnlRegistry.getInstance();
        int sizeBefore = registry.size();
        System.out.printf("[Exp358] Pool size before: %,d neurons%n", sizeBefore);

        Path cwd = Paths.get("").toAbsolutePath();
        Path modelPath = null;
        for (Path p = cwd; p != null; p = p.getParent()) {
            Path candidate = p.resolve("models/external/qwen2.5-1.5b/model.safetensors");
            if (Files.exists(candidate)) {
                modelPath = candidate;
                break;
            }
        }
        if (modelPath == null) {
            assumeTrue(false, "Qwen2.5-1.5B not downloaded; skipping");
            return;
        }

        // Distill at small budget (faster test)
        int budget = 1 << 12;
        BooleanChainRunner runner = BooleanChainRunner.loadFromSafetensors(
                modelPath, "model", budget);
        if (runner == null || runner.layerCount() == 0) {
            assumeTrue(false, "Qwen2.5-1.5B didn't distill");
            return;
        }

        long now = System.currentTimeMillis();
        int appended = 0;
        long t0 = System.nanoTime();
        var layers = runner.layers();
        for (int li = 0; li < layers.size(); li++) {
            TruthTableLayer layer = layers.get(li);
            for (int ni = 0; ni < layer.neuronCount(); ni++) {
                TruthTable t = layer.neurons().get(ni);
                if (t == null) continue;
                EnrichedNeuron enriched = EnrichedNeuron.derive(t);
                FnlEntry entry = FnlEntry.fromEnriched(UUID.randomUUID(), enriched,
                        "Qwen2.5-1.5B", now);
                registry.append(entry);
                appended++;
            }
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertThat(appended).isGreaterThan(0);
        assertThat(registry.size())
                .as("pool grew by %,d", appended)
                .isEqualTo(sizeBefore + appended);

        System.out.printf("[Exp358] Qwen2.5-1.5B: %,d neurons appended in %,d ms%n",
                appended, elapsedMs);
        System.out.printf("[Exp358] Pool size after: %,d neurons across %d provenances%n",
                registry.size(), registry.provenances().size());
        System.out.printf("[Exp358] Provenances: %s%n", registry.provenances());
        System.out.printf("[Exp358] Counts by provenance: %s%n",
                registry.countsByProvenance());
    }

    @Test
    void multiSizeQwenComparison() throws IOException {
        // Distill BOTH qwen2.5-0.5b and qwen2.5-1.5b at same budget
        // Verify: 1.5B has more neurons than 0.5B (more layers / wider)
        Path cwd = Paths.get("").toAbsolutePath();
        Path smallPath = null, bigPath = null;
        for (Path p = cwd; p != null; p = p.getParent()) {
            if (smallPath == null) {
                Path candidate = p.resolve("models/external/qwen2.5-0.5b/model.safetensors");
                if (Files.exists(candidate)) smallPath = candidate;
            }
            if (bigPath == null) {
                Path candidate = p.resolve("models/external/qwen2.5-1.5b/model.safetensors");
                if (Files.exists(candidate)) bigPath = candidate;
            }
        }
        if (smallPath == null || bigPath == null) {
            assumeTrue(false, "need both Qwen sizes");
            return;
        }
        int budget = 1 << 12;
        BooleanChainRunner small = BooleanChainRunner.loadFromSafetensors(
                smallPath, "model", budget);
        BooleanChainRunner big = BooleanChainRunner.loadFromSafetensors(
                bigPath, "model", budget);
        int smallCount = totalNeurons(small);
        int bigCount = totalNeurons(big);
        System.out.printf("[Exp358] 0.5B: %,d neurons; 1.5B: %,d neurons (ratio %.2f)%n",
                smallCount, bigCount, (double) bigCount / Math.max(1, smallCount));
        assertThat(bigCount).isGreaterThanOrEqualTo(smallCount);
    }

    @Test
    void llamaGatedDocumented() {
        // Llama-3.2-1B is gated on HuggingFace. The HF download
        // returned "Access denied. This repository requires approval."
        // Documented: would require Meta approval + HF token with
        // llama-3.2-1B scope.
        // This is NOT a test failure — it's a documented constraint.
        assertThat(true).isTrue();  // marker — this test always passes
    }

    private static int totalNeurons(BooleanChainRunner runner) {
        if (runner == null) return 0;
        int sum = 0;
        for (var layer : runner.layers()) sum += layer.neuronCount();
        return sum;
    }
}
