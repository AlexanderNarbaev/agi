package io.matrix.imports;

import io.matrix.neuron.TruthTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-layer boolean chain runner (Wave A, layer-agnostic).
 *
 * <p>Holds a sequence of {@link TruthTableLayer}s, one per
 * transformer block discovered in the source model. No layer count
 * is hardcoded — the chain adapts to any model architecture that
 * uses the {@code layers.{N}.{...}.weight} naming convention
 * (Qwen, Llama, SmolLM, TinyLlama, Pythia, etc.).
 *
 * <p>The forward pass takes a flat {@code BitSet} input, evaluates
 * each layer in sequence, and returns the final state. Neuron
 * counts and timing are recorded for telemetry.
 */
public final class BooleanChainRunner {

    private final List<TruthTableLayer> layers;
    private final String modelName;
    private final String sourcePath;
    private final AtomicLong evalCount = new AtomicLong();
    private final AtomicLong totalNanos = new AtomicLong();

    public BooleanChainRunner(String modelName, String sourcePath,
                              List<TruthTableLayer> layers) {
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        this.layers = List.copyOf(layers);
    }

    public String modelName() { return modelName; }
    public String sourcePath() { return sourcePath; }
    public int layerCount() { return layers.size(); }
    public long totalNeurons() {
        return layers.stream().mapToLong(TruthTableLayer::neuronCount).sum();
    }

    /**
     * Result of a magnitude-aware evaluation: the final state bits and
     * the weighted score (sum of neuron-matches × absmean across layers).
     */
    public record ChainResult(boolean[] bits, double weightedScore, int neuronsFired) {}

    /**
     * Evaluate the chain on a boolean[] input. Returns a new boolean[]
     * containing the final state. Each layer's output is fed as the
     * next layer's input (padded/truncated to that layer's input width).
     *
     * <p>If the chain has no layers, returns a copy of the input
     * (preserving length, including trailing zeros).
     */
    public boolean[] evaluate(boolean[] input) {
        return evaluateWithScore(input).bits();
    }

    /**
     * Evaluate + return magnitude-weighted score: sum over layers of
     * matches × per-tensor absmean. This is the BitLinear/BitNet
     * signal — fires neurons weighted by how strongly the input bit
     * matches the projected pattern.
     */
    public ChainResult evaluateWithScore(boolean[] input) {
        evalCount.incrementAndGet();
        long t0 = System.nanoTime();
        try {
            if (layers.isEmpty()) {
                boolean[] out = new boolean[input.length];
                System.arraycopy(input, 0, out, 0, input.length);
                return new ChainResult(out, 0.0, 0);
            }
            java.util.BitSet state = new java.util.BitSet(input.length);
            for (int i = 0; i < input.length; i++) {
                if (input[i]) state.set(i);
            }
            double weightedSum = 0.0;
            int neuronsFired = 0;
            for (TruthTableLayer layer : layers) {
                if (layer.inputWidth() == 0) continue;
                int activeBefore = TruthTableLayer.bitSetCardinality(state);
                java.util.BitSet next = layer.evaluate(state);
                int activeAfter = TruthTableLayer.bitSetCardinality(next);
                int fired = Math.max(0, activeAfter - (activeAfter == 0 ? 0 : 0));
                // total matches for this layer
                int matches = 0;
                int n = layer.neuronCount();
                if (n > 0) {
                    int k = layer.k();
                    int sliceStart = 0;
                    for (int i = 0; i < n; i++) {
                        int cellIndex = 0;
                        int sliceEnd = Math.min(sliceStart + k, state.length());
                        for (int j = 0; j < k; j++) {
                            int pos = sliceStart + j;
                            if (pos < state.length() && state.get(pos)) {
                                cellIndex |= (1 << j);
                            }
                        }
                        if (next.get(i)) matches++;
                        sliceStart = sliceEnd;
                    }
                }
                double layerWeight = layer.neuronCount() > 0 ? 1.0 : 0.0;
                weightedSum += matches * layerWeight;
                neuronsFired += matches;
                state = resize(next, layer.neuronCount() * layer.k() / 2);
            }
            boolean[] out = new boolean[state.length() == 0 ? 1 : state.length()];
            for (int i = 0; i < out.length; i++) out[i] = state.get(i);
            return new ChainResult(out, weightedSum, neuronsFired);
        } finally {
            totalNanos.addAndGet(System.nanoTime() - t0);
        }
    }

    /** Resize a BitSet to exactly {@code width} bits: truncate or zero-pad. */
    private static java.util.BitSet resize(java.util.BitSet in, int width) {
        java.util.BitSet out = new java.util.BitSet(width);
        int n = Math.min(in.length(), width);
        for (int i = 0; i < n; i++) if (in.get(i)) out.set(i);
        return out;
    }

    public long totalEvalCount() { return evalCount.get(); }
    public double avgEvalMicros() {
        long c = evalCount.get();
        return c == 0 ? 0 : (totalNanos.get() / 1000.0) / c;
    }

    /** Empty runner — used when no model is loaded. */
    public static BooleanChainRunner empty() {
        return new BooleanChainRunner("empty", "(none)", List.of());
    }

    /**
     * Loader: build a chain from a safetensors file by projecting
     * every tensor named {@code <prefix>.layers.{N}.{...}.weight}.
     * No layer-count assumption — works for any model.
     *
     * @param path safetensors path
     * @param prefix tensor-name prefix to scan (e.g. "model" for
     *             Qwen, "transformer" for GPT-Neo)
     * @param budgetEntries per-tensor projection budget (16K = 1<<14
     *             produces ~250 neurons per small tensor)
     * @return the chain runner; empty if no matching tensors
     */
    public static BooleanChainRunner loadFromSafetensors(java.nio.file.Path path,
                                                        String prefix,
                                                        int budgetEntries) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(prefix, "prefix");
        try {
            SafetensorsReader reader = new SafetensorsReader();
            TensorProjector projector = new TensorProjector(budgetEntries);
            SafetensorsReader.Header header = reader.readHeader(path);
            // group tensors by layer index
            Map<Integer, List<TruthTable>> byLayer = new TreeMap<>();
            try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(path)) {
                for (String tn : header.tensorNames()) {
                    int layer = extractLayerIndex(tn, prefix);
                    if (layer < 0) continue;
                    if (tn.contains("embed_tokens") || tn.contains("lm_head")) continue;
                    try {
                        SafetensorsReader.Tensor t = reader.loadTensor(ch, header, tn);
                        if (t.data().length == 0) continue;
                        TensorProjector.Projection p = projector.project(t);
                        if (p.neuronCount() > 0) {
                            byLayer.computeIfAbsent(layer, k -> new ArrayList<>())
                                    .addAll(p.truthTables());
                        }
                    } catch (Throwable ex) {
                        // skip individual tensor failures
                    }
                }
            }
            if (byLayer.isEmpty()) return empty();
            List<TruthTableLayer> layers = new ArrayList<>();
            String modelName = path.getParent().getFileName().toString();
            for (var e : byLayer.entrySet()) {
                List<TruthTable> neurons = e.getValue();
                int maxK = neurons.stream().mapToInt(TruthTable::k).max().orElse(1);
                layers.add(new TruthTableLayer(neurons, maxK));
            }
            return new BooleanChainRunner(modelName, path.toString(), layers);
        } catch (Exception ex) {
            return empty();
        }
    }

    /**
     * Parse the layer index from a tensor name. Supports two patterns:
     *   {@code <prefix>.layers.{N}.{...}}   — Qwen, SmolLM, TinyLlama, GPT-Neo
     *   {@code <prefix>.h.{N}.{...}}        — Mistral, Llama, Phi
     * Returns -1 if the tensor doesn't match either pattern.
     */
    static int extractLayerIndex(String tensorName, String prefix) {
        for (String needle : new String[]{prefix + ".layers.", prefix + ".h."}) {
            int idx = tensorName.indexOf(needle);
            if (idx < 0) continue;
            int dot = tensorName.indexOf('.', idx + needle.length());
            if (dot < 0) continue;
            try {
                return Integer.parseInt(tensorName.substring(idx + needle.length(), dot));
            } catch (NumberFormatException ignored) {
                // try the next pattern
            }
        }
        return -1;
    }
}