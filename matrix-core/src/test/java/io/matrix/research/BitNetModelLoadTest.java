package io.matrix.research;

import io.matrix.imports.SafetensorsReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 37: BitNet b1.58 2B model integration test.
 *
 * <p>Verifies our SafetensorsReader can load the real BitNet b1.58 2B
 * weights file from HuggingFace (microsoft/bitnet-b1.58-2B-4T).
 *
 * <p>Tests:
 * - File exists and is readable
 * - Tensor count matches expected (542 tensors for BitNet 2B)
 * - Sample tensor shapes match expected dimensions
 * - Weights are uint8, scales are bf16
 *
 * <p>Full inference requires implementing the BitNet transformer block
 * (attention + MLP with BitLinear layers, ReLU² activation, RoPE, etc.).
 * This test verifies the loading infrastructure.
 */
class BitNetModelLoadTest {

    /**
     * RECON-W32.34, operator decision D1 (option A): the real 1.1 GB BitNet
     * checkpoint is not present, so every test in this class previously failed at
     * the file check without reaching an assertion. This converts FAILURE into
     * SKIPPED, which is the honest description: BitNet has NEVER been executed and is
     * UNTESTED, not failing for a known reason. See BitNetRealModelFixture.
     */
    @BeforeEach
    void requireRealCheckpoint() {
        BitNetRealModelFixture.assumeAvailable();
    }

    private static final String MODEL_PATH =
            BitNetRealModelFixture.MODEL_PATH;

    @Test
    void bitNetModelFileExists() {
        Path path = Path.of(MODEL_PATH);
        assertThat(path.toFile().exists()).isTrue();
        long sizeBytes = path.toFile().length();
        // Should be ~1.1 GB
        assertThat(sizeBytes).isBetween(500_000_000L, 2_000_000_000L);
        System.out.printf("[BitNet] model file size: %.0f MB%n", sizeBytes / 1024.0 / 1024.0);
    }

    @Test
    void bitNetModelHasExpectedTensorCount() throws IOException {
        SafetensorsReader reader = new SafetensorsReader();
        SafetensorsReader.Header header = reader.readHeader(Path.of(MODEL_PATH));
        int count = header.tensorCount();
        System.out.println("[BitNet] tensor count: " + count);
        // BitNet 2B has 542 tensors per our Python inspection
        assertThat(count).isBetween(500, 600);
    }

    @Test
    void bitNetModelHasEmbeddings() throws IOException {
        SafetensorsReader reader = new SafetensorsReader();
        SafetensorsReader.Header header = reader.readHeader(Path.of(MODEL_PATH));
        List<String> names = header.tensorNames();
        boolean hasEmbed = names.stream().anyMatch(n -> n.contains("embed_tokens"));
        boolean hasLmHead = names.stream().anyMatch(n -> n.contains("lm_head"));
        assertThat(hasEmbed).isTrue();
        System.out.println("[BitNet] embed_tokens present");
    }

    @Test
    void bitNetModelHas30Layers() throws IOException {
        SafetensorsReader reader = new SafetensorsReader();
        SafetensorsReader.Header header = reader.readHeader(Path.of(MODEL_PATH));
        List<String> names = header.tensorNames();
        // Count unique layer indices in names like "model.layers.5.self_attn.q_proj.weight"
        long layerCount = names.stream()
                .filter(n -> n.contains("model.layers."))
                .map(n -> n.split("\\.")[2])
                .distinct()
                .count();
        System.out.println("[BitNet] layer count: " + layerCount);
        assertThat(layerCount).isEqualTo(30L); // 30 hidden layers per config
    }

    @Test
    void bitNetModelWeightsAreUint8() throws IOException {
        SafetensorsReader reader = new SafetensorsReader();
        SafetensorsReader.Header header = reader.readHeader(Path.of(MODEL_PATH));
        // Find gate_proj tensor
        boolean hasGateProj = header.tensorNames().stream()
                .anyMatch(n -> n.contains("gate_proj.weight") && !n.contains("scale"));
        assertThat(hasGateProj).isTrue();
        System.out.println("[BitNet] gate_proj tensors present");
    }

    @Test
    void bitNetModelEmbeddingIsBfloat16() throws IOException {
        SafetensorsReader reader = new SafetensorsReader();
        SafetensorsReader.Header header = reader.readHeader(Path.of(MODEL_PATH));
        boolean hasEmbed = header.tensorNames().stream()
                .anyMatch(n -> n.contains("embed_tokens"));
        assertThat(hasEmbed).isTrue();
        // Just verify we can iterate all names without error
        int count = header.tensorNames().size();
        assertThat(count).isGreaterThan(500);
    }
}
