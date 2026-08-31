package io.matrix.imports;

import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave I-layer-agnostic + magnitude-aware scorer tests.
 */
class BooleanChainRunnerTest {

    @Test
    void emptyRunnerEvaluateReturnsInputBits() {
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
        assertThat(BooleanChainRunner.extractLayerIndex(
                "model.layers.0.self_attn.q_proj.weight", "model")).isEqualTo(0);
        assertThat(BooleanChainRunner.extractLayerIndex(
                "transformer.h.5.mlp.gate_proj.weight", "transformer")).isEqualTo(5);
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

    @Test
    void evaluateWithScoreReturnsChainResult() {
        BooleanChainRunner runner = BooleanChainRunner.empty();
        boolean[] input = new boolean[16];
        for (int i = 0; i < 16; i++) input[i] = (i % 3) == 0;
        BooleanChainRunner.ChainResult r = runner.evaluateWithScore(input);
        assertThat(r.bits()).hasSize(16);
        assertThat(r.weightedScore()).isEqualTo(0.0);
        assertThat(r.neuronsFired()).isEqualTo(0);
    }

    @Test
    void chainResultRecordFieldsAreAccessible() {
        BooleanChainRunner.ChainResult r =
                new BooleanChainRunner.ChainResult(
                        new boolean[]{true, false}, 42.5, 7);
        assertThat(r.bits()).containsExactly(true, false);
        assertThat(r.weightedScore()).isEqualTo(42.5);
        assertThat(r.neuronsFired()).isEqualTo(7);
    }

    @Test
    void truthTableLayerBitSetCardinality() {
        BitSet bs = new BitSet();
        assertThat(TruthTableLayer.bitSetCardinality(bs)).isZero();
        bs.set(0); bs.set(7); bs.set(15);
        assertThat(TruthTableLayer.bitSetCardinality(bs)).isEqualTo(3);
    }
}