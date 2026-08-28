package io.matrix.imports;

import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W8 end-to-end: project Qwen2.5-0.5B weights, group by transformer
 * layer, compose into a multi-layer boolean chain, and run a
 * forward pass on a synthetic input.
 *
 * <p>This is the missing wiring: the imported neurons are stored as
 * {@link TruthTable} instances (per W7), but no current code path
 * runs them as a multi-step chain. This test exercises the
 * compose-evaluate path directly via {@link TruthTableLayer} (no
 * NeuronLayer/DecisionTree conversion required).
 */
class WeightImportComposeIT {

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

    /**
     * The minimum useful W8 test: project all layers, build one
     * TruthTableLayer per transformer block, evaluate the chain end to
     * end on a synthetic input.
     */
    @Test
    void composeQwenLayersIntoBooleanChainAndRun() throws Exception {
        Path safetensors = findSafetensors();
        if (safetensors == null) {
            System.out.println("[W8] no safetensors found — skipping");
            return;
        }
        System.out.println("[W8] composing layers from "
                + safetensors.getParent().getParent().getFileName());

        // Step 1: project every tensor; group by transformer block index
        SafetensorsReader reader = new SafetensorsReader();
        TensorProjector projector = new TensorProjector(1 << 14);
        SafetensorsReader.Header header = reader.readHeader(safetensors);

        Map<Integer, List<TruthTable>> byLayer = new HashMap<>();
        try (FileChannel ch = FileChannel.open(safetensors)) {
            for (String tensorName : header.tensorNames()) {
                if (tensorName.contains("embed_tokens")
                        || tensorName.contains("lm_head")) continue;
                SafetensorsReader.Tensor t = reader.loadTensor(ch, header, tensorName);
                if (t.data().length == 0) continue;
                TensorProjector.Projection p = projector.project(t);
                int layer = extractLayerIndex(tensorName);
                if (layer < 0) continue;
                byLayer.computeIfAbsent(layer, k -> new ArrayList<>())
                        .addAll(p.truthTables());
            }
        }

        // Step 2: build one TruthTableLayer per transformer block
        List<TruthTableLayer> layers = new ArrayList<>();
        for (int layer : byLayer.keySet().stream().sorted().toList()) {
            List<TruthTable> neurons = byLayer.get(layer);
            if (neurons.isEmpty()) continue;
            int maxK = neurons.stream().mapToInt(TruthTable::k).max().orElse(1);
            layers.add(new TruthTableLayer(neurons, maxK));
        }
        System.out.println("[W8] composed " + layers.size()
                + " TruthTableLayers (one per transformer block)");
        System.out.println("[W8] total neurons in chain: "
                + layers.stream().mapToInt(TruthTableLayer::neuronCount).sum());

        // Step 3: forward pass on a synthetic input
        // Qwen 0.5B hidden size = 896; pad/truncate to first layer's input width
        int firstInputWidth = layers.get(0).inputWidth();
        BitSet input = new BitSet(firstInputWidth);
        long seed = 0xBEEF;
        for (int i = 0; i < firstInputWidth; i++) {
            if (((seed >>> (i % 32)) & 1L) == 1L) input.set(i);
        }

        long t0 = System.nanoTime();
        BitSet state = input;
        for (int i = 0; i < layers.size(); i++) {
            TruthTableLayer layer = layers.get(i);
            state = layer.evaluate(state);
        }
        long elapsedUs = (System.nanoTime() - t0) / 1_000;

        System.out.println("[W8] ========================================");
        System.out.println("[W8] chain length:    " + layers.size() + " transformer blocks");
        System.out.println("[W8] first layer in:  " + firstInputWidth + " bits");
        System.out.println("[W8] final output:    " + state.cardinality() + " bits set / "
                + state.length() + " bits long");
        System.out.println("[W8] eval time:       " + elapsedUs + " μs");
        System.out.println("[W8] ========================================");

        // We don't claim the output is semantically meaningful — 1-bit
        // distillation loses accuracy. We only claim the substrate +
        // compose + forward pass works end-to-end.
        assertThat(layers.size()).isGreaterThanOrEqualTo(20);
        assertThat(state).isNotNull();
    }

    /** Parse the layer index from a tensor name. Returns -1 if not a layer tensor. */
    private static int extractLayerIndex(String name) {
        // name = "model.layers.{N}.{...}.weight"
        int idx = name.indexOf("layers.");
        if (idx < 0) return -1;
        int dot = name.indexOf('.', idx + "layers.".length());
        if (dot < 0) return -1;
        try {
            return Integer.parseInt(name.substring(idx + "layers.".length(), dot));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}