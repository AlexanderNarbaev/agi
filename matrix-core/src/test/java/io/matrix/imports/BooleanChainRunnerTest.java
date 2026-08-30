package io.matrix.imports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave I tests for BooleanChainRunner.evaluate(): the new resize
 * semantics (truncate OR zero-pad to the next layer's input width),
 * the chain metric tracking, and edge cases.
 */
class BooleanChainRunnerTest {

    @Test
    void emptyRunnerEvaluateReturnsInputBits() {
        // empty chain (no layers) — should still return boolean[]
        // matching the input width
        BooleanChainRunner runner = BooleanChainRunner.empty();
        boolean[] input = new boolean[]{true, false, true, false};
        boolean[] out = runner.evaluate(input);
        assertThat(out).hasSize(4);
        assertThat(out).containsExactly(true, false, true, false);
    }

    @Test
    void emptyRunnerRecordsEvaluations() {
        BooleanChainRunner runner = BooleanChainRunner.empty();
        assertThat(runner.totalEvalCount()).isZero();
        assertThat(runner.avgEvalMicros()).isEqualTo(0.0);
        runner.evaluate(new boolean[]{true, false});
        assertThat(runner.totalEvalCount()).isEqualTo(1);
    }

    @Test
    void runnerExposesMetadata() {
        BooleanChainRunner runner = new BooleanChainRunner(
                "test-model", "/path/to/model", java.util.List.of());
        assertThat(runner.modelName()).isEqualTo("test-model");
        assertThat(runner.sourcePath()).isEqualTo("/path/to/model");
        assertThat(runner.layerCount()).isZero();
        assertThat(runner.totalNeurons()).isZero();
    }

    @Test
    void extractLayerIndexHandlesVariants() {
        // Qwen pattern
        assertThat(BooleanChainRunner.extractLayerIndex(
                "model.layers.0.self_attn.q_proj.weight", "model")).isEqualTo(0);
        // Mistral/transformer-style prefix
        assertThat(BooleanChainRunner.extractLayerIndex(
                "transformer.h.5.mlp.gate_proj.weight", "transformer")).isEqualTo(5);
        // high layer index
        assertThat(BooleanChainRunner.extractLayerIndex(
                "model.layers.23.mlp.down_proj.weight", "model")).isEqualTo(23);
    }

    @Test
    void extractLayerIndexRejectsNonMatchingPrefix() {
        assertThat(BooleanChainRunner.extractLayerIndex(
                "model.layers.0.q_proj.weight", "transformer")).isEqualTo(-1);
        assertThat(BooleanChainRunner.extractLayerIndex(
                "other.thing", "model")).isEqualTo(-1);
        assertThat(BooleanChainRunner.extractLayerIndex(
                "model.embed_tokens.weight", "model")).isEqualTo(-1);
        assertThat(BooleanChainRunner.extractLayerIndex(
                "model.norm.weight", "model")).isEqualTo(-1);
    }

    @Test
    void loadFromSafetensorsReturnsEmptyForMissingFile() {
        var result = BooleanChainRunner.loadFromSafetensors(
                java.nio.file.Path.of("/no/such/file.safetensors"),
                "model", 1024);
        assertThat(result.layerCount()).isZero();
        assertThat(result.totalEvalCount()).isZero();
    }
}