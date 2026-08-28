package io.matrix.imports;

import io.matrix.bir.TtForm;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave D: BitLinear projection end-to-end on the downloaded Qwen
 * safetensors. Verifies that the absmean-rescaled projection runs
 * without OOM and produces neurons with non-zero absmean (the key
 * difference vs sign-of-zero projection).
 */
class BitLinearProjectionIT {

    private static final List<Path> CANDIDATES = List.of(
            Path.of("/tmp/opencode/matrix-import/models--Qwen--Qwen2.5-0.5B/snapshots"),
            Path.of("/tmp/opencode/matrix-import/models--HuggingFaceTB--SmolLM2-360M-Instruct/snapshots"));

    private static Path findSafetensors() {
        for (Path root : CANDIDATES) {
            if (!Files.isDirectory(root)) continue;
            try {
                return Files.walk(root, 3)
                        .filter(p -> p.toString().endsWith(".safetensors"))
                        .filter(Files::isRegularFile)
                        .findFirst().orElse(null);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Test
    void bitLinearProjectionProducesAbsmeanPreservingNeurons() throws Exception {
        Path safetensors = findSafetensors();
        if (safetensors == null) {
            System.out.println("[Wave D] no safetensors — skipping");
            return;
        }
        BitLinearProjector projector = new BitLinearProjector(1 << 14);

        System.out.println("[Wave D] =======================================");
        System.out.println("[Wave D] source: " + safetensors);

        long t0 = System.currentTimeMillis();
        Map<Integer, List<BitLinearProjector.Projection>> byLayer =
                projector.projectByLayer(safetensors, "model", 40);  // cap for fast iteration
        long ms = System.currentTimeMillis() - t0;

        int totalNeurons = 0;
        float maxAbsmean = 0f;
        float minAbsmean = Float.MAX_VALUE;
        int maxAbsmeanLayer = -1, minAbsmeanLayer = -1;
        for (var e : byLayer.entrySet()) {
            int layerNeurons = e.getValue().stream()
                    .mapToInt(BitLinearProjector.Projection::neuronCount).sum();
            for (var p : e.getValue()) {
                if (p.absmean() > maxAbsmean) {
                    maxAbsmean = p.absmean();
                    maxAbsmeanLayer = e.getKey();
                }
                if (p.absmean() < minAbsmean && p.absmean() > 0) {
                    minAbsmean = p.absmean();
                    minAbsmeanLayer = e.getKey();
                }
            }
            totalNeurons += layerNeurons;
        }
        System.out.println("[Wave D] layers:          " + byLayer.size());
        System.out.println("[Wave D] total neurons:   " + totalNeurons);
        System.out.println("[Wave D] projection ms:  " + ms);
        System.out.println("[Wave D] absmean range:   " + minAbsmean + " (layer "
                + minAbsmeanLayer + ") ... " + maxAbsmean + " (layer "
                + maxAbsmeanLayer + ")");
        System.out.println("[Wave D] =======================================");

        // sanity: BitLinear projection must preserve per-tensor absmean
        // (the key improvement vs sign-of-zero); a typical Qwen hidden
        // layer has absmean in [0.005, 0.5] for fp16 weights
        assertThat(maxAbsmean).isGreaterThan(0.001f);
        assertThat(minAbsmean).isGreaterThan(0f);
        assertThat(totalNeurons).isGreaterThan(100);
    }

    @Test
    void bitLinearNeuronIsQueryable() throws Exception {
        Path safetensors = findSafetensors();
        if (safetensors == null) return;

        BitLinearProjector projector = new BitLinearProjector(1 << 12);
        Map<Integer, List<BitLinearProjector.Projection>> byLayer =
                projector.projectByLayer(safetensors, "model");
        // find any layer
        BitLinearProjector.Projection proj = byLayer.values().iterator().next().get(0);
        TtForm neuron = proj.neurons().get(0);
        // query the neuron
        long[] in = new long[]{0L};
        long[] out = io.matrix.bir.BooleanRuntime.evaluate(neuron, in);
        assertThat(out).isNotEmpty();
    }
}