package io.matrix.imports;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave I-layer-autodiscovery: verify that
 * {@link BooleanChainRunner#extractLayerIndex} correctly reads the
 * "layers.{N}" suffix from any model's tensor names, regardless of
 * model architecture. Runs against every safetensors found in
 * {@code models/external/} and asserts the discovered layer counts
 * match the published model specs.
 */
class LayerAutodiscoveryTest {

    /**
     * Map: model dir name → expected layer count.
     * Verified against each model's published config (Qwen2.5-0.5B
     * has 24 layers, TinyLlama 1.1B has 22 layers).
     */
    private static final Map<String, Integer> EXPECTED_LAYERS = Map.of(
            "qwen2.5-0.5b", 24,
            "tinyllama-1.1b", 22
    );

    /** Project root (matrix-core/ is the test cwd). */
    private static final Path REPO_ROOT = Path.of("").toAbsolutePath().getParent();

    @Test
    void autoDiscoveryMatchesPublishedSpecs() throws Exception {
        // scan every downloaded model in models/external
        Path externalDir = REPO_ROOT.resolve("models/external");
        if (!Files.isDirectory(externalDir)) {
            System.out.println("[Wave I] models/external missing — skipping");
            return;
        }
        int tested = 0;
        try (var stream = Files.list(externalDir)) {
            for (Path modelDir : stream.filter(Files::isDirectory).toList()) {
                String name = modelDir.getFileName().toString();
                Integer expected = EXPECTED_LAYERS.get(name);
                if (expected == null) continue;  // skip unverified models
                Path safetensors = findSafetensors(modelDir);
                if (safetensors == null) continue;
                int discovered = maxLayerIndex(safetensors);
                System.out.printf("[Wave I] %s: discovered %d layers, expected %d%n",
                        name, discovered, expected);
                assertThat(discovered)
                        .as("layer-autodiscovery for " + name)
                        .isEqualTo(expected);
                tested++;
            }
        }
        // we should have tested at least one model; if none available,
        // skip without failing (CI may not have downloaded models)
        if (tested == 0) {
            System.out.println("[Wave I] no downloaded models to test against");
        }
    }

    @Test
    void extractLayerIndexHandlesQwenAndMistralNamingConventions() {
        // Qwen: "model.layers.0.self_attn.q_proj.weight"
        assertThat(BooleanChainRunner.extractLayerIndex(
                "model.layers.0.self_attn.q_proj.weight", "model")).isEqualTo(0);
        assertThat(BooleanChainRunner.extractLayerIndex(
                "model.layers.23.mlp.down_proj.weight", "model")).isEqualTo(23);

        // Mistral/Llama: "model.layers.5.mlp.gate_proj.weight"
        assertThat(BooleanChainRunner.extractLayerIndex(
                "model.layers.5.mlp.gate_proj.weight", "model")).isEqualTo(5);

        // non-matching prefix → -1
        assertThat(BooleanChainRunner.extractLayerIndex(
                "other.layers.0.weight", "model")).isEqualTo(-1);

        // malformed: returns -1 (caller handles as not-a-layer-tensor)
        assertThat(BooleanChainRunner.extractLayerIndex(
                "model.embed_tokens.weight", "model")).isEqualTo(-1);
        assertThat(BooleanChainRunner.extractLayerIndex(
                "model.norm.weight", "model")).isEqualTo(-1);
    }

    @Test
    void runOnQwenSafetensorsIfPresent() throws Exception {
        Path qwenSafetensors = findSafetensors(REPO_ROOT.resolve("models/external/qwen2.5-0.5b"));
        if (qwenSafetensors == null) {
            System.out.println("[Wave I] Qwen safetensors not present");
            return;
        }
        int maxLayer = maxLayerIndex(qwenSafetensors);
        // Qwen2.5-0.5B has 24 hidden layers + 0 input embed
        assertThat(maxLayer)
                .as("Qwen2.5-0.5B should have 24 transformer blocks (0..23)")
                .isEqualTo(23);
    }

    private static Path findSafetensors(Path dir) {
        try {
            return Files.walk(dir, 4)
                    .filter(p -> p.toString().endsWith(".safetensors"))
                    .filter(Files::isRegularFile)
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static int maxLayerIndex(Path safetensors) throws IOException {
        Map<Integer, Integer> byLayer = new TreeMap<>();
        try (var ch = Files.newByteChannel(safetensors)) {
            io.matrix.imports.SafetensorsReader reader = new io.matrix.imports.SafetensorsReader();
            io.matrix.imports.SafetensorsReader.Header header = reader.readHeader(safetensors);
            for (String tn : header.tensorNames()) {
                int layer = BooleanChainRunner.extractLayerIndex(tn, "model");
                if (layer < 0) layer = BooleanChainRunner.extractLayerIndex(tn, "transformer");
                if (layer < 0) continue;
                byLayer.merge(layer, 1, Integer::sum);
            }
        }
        return byLayer.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
    }
}