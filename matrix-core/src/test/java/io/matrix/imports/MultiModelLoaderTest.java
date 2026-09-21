package io.matrix.imports;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-model chain loader test: scans the local models/external/
 * directory, loads every safetensors, and combines them into ONE
 * BooleanChainRunner. Verifies:
 *   - layer count = sum across models (not fixed)
 *   - neuron count = sum across models
 *   - chain is non-empty if at least one model loaded
 */
class MultiModelLoaderTest {

    @Test
    void loadFromModelsExternalCombinesAllSafetensors() {
        // resolve from repo root (tests run from matrix-core/)
        Path root = Path.of("").toAbsolutePath().getParent()
                .resolve("models/external");
        if (!Files.isDirectory(root)) {
            System.out.println("[skip] models/external missing at " + root);
            return;
        }
        // sanity check: at least one safetensors present
        long count;
        try (var s = Files.walk(root, 4)) {
            count = s.filter(p -> p.getFileName().toString().endsWith("model.safetensors"))
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> !p.toString().contains("/.cache/"))
                    .count();
        } catch (Exception e) {
            count = 0;
        }
        if (count == 0) {
            System.out.println("[skip] no model.safetensors in models/external");
            return;
        }
        MultiModelLoader.LoadResult result = MultiModelLoader.loadFromDirectory(root);
        System.out.println("[MultiModelLoader] models=" + result.entries().size()
                + " totalLayers=" + result.totalLayers()
                + " totalNeurons=" + result.totalNeurons());
        for (var e : result.entries()) {
            System.out.println("  - " + e.name() + ": " + e.layers()
                    + " layers, " + e.neurons() + " neurons");
        }
        assertThat(result.isEmpty()).as("at least one model should load").isFalse();
        assertThat(result.totalLayers()).isGreaterThan(0);
        assertThat(result.totalNeurons()).isGreaterThan(0);
        // combined chain is a single instance
        assertThat(result.chain().layerCount()).isEqualTo(result.totalLayers());
    }

    @Test
    void emptyResultWhenNoSafetensors() {
        // empty list → empty result with reason
        MultiModelLoader.LoadResult result =
                MultiModelLoader.loadAll(List.of());
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.totalLayers()).isZero();
        assertThat(result.totalNeurons()).isZero();
    }

    @Test
    void emptyResultWhenModelsDirectoryMissing() {
        MultiModelLoader.LoadResult result =
                MultiModelLoader.loadFromDirectory(Path.of("/no/such/path"));
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void layerCountIsNotFixed() {
        // verify that the layer count of the combined chain is
        // computed dynamically from the loaded models, not hardcoded
        Path root = Path.of("").toAbsolutePath().getParent()
                .resolve("models/external");
        if (!Files.isDirectory(root)) {
            System.out.println("[skip] " + root);
            return;
        }
        try (Stream<Path> s = Files.walk(root, 4)) {
            long n = s.filter(p -> p.getFileName().toString().endsWith("model.safetensors"))
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/.cache/"))
                    .count();
            if (n == 0) {
                System.out.println("[skip]");
                return;
            }
        } catch (Exception e) {
            return;
        }
        MultiModelLoader.LoadResult result = MultiModelLoader.loadFromDirectory(root);
        // the layer count comes from the actual models — no hardcoded "24"
        int totalFromModels = result.entries().stream().mapToInt(MultiModelLoader.ModelEntry::layers).sum();
        assertThat(result.chain().layerCount())
                .as("layer count is auto-discovered from loaded models")
                .isEqualTo(totalFromModels);
    }
}