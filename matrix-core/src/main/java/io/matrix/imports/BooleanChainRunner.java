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
    private volatile java.util.List<long[]> tablesForNative;
    private volatile int kForNative;
    private volatile boolean useNative;
    private volatile io.matrix.imports.PanamaNativeBridge panamaBridge;

    public BooleanChainRunner(String modelName, String sourcePath,
                              List<TruthTableLayer> layers) {
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        this.layers = List.copyOf(layers);
        this.tablesForNative = null;
        this.kForNative = 0;
        this.useNative = false;
        this.panamaBridge = null;
    }

    /** Internal: construct a runner that has pre-computed native tables for
     * fast C-level evaluation via {@link PanamaNativeBridge}. */
    public BooleanChainRunner(String modelName, String sourcePath,
                              List<TruthTableLayer> layers,
                              List<long[]> tablesForNative, int kForNative,
                              boolean useNative) {
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        this.layers = List.copyOf(layers);
        this.tablesForNative = tablesForNative;
        this.kForNative = kForNative;
        this.useNative = useNative;
        this.panamaBridge = null;  // inject via BooleanChainProducer or set later
    }

    /** Inject the Panama native bridge for fast C-level evaluation. */
    public void setPanamaBridge(io.matrix.imports.PanamaNativeBridge bridge) {
        this.panamaBridge = bridge;
    }
    /** Set pre-computed native tables and k value for fast evaluation. */
    public void setNativeTables(java.util.List<long[]> tables, int k) {
        this.tablesForNative = tables;
        this.kForNative = k;
    }
    /** Enable or disable the native evaluation fast path. */
    public void setUseNative(boolean use) {
        this.useNative = use;
    }

    public String modelName() { return modelName; }
    public String sourcePath() { return sourcePath; }
    public int layerCount() { return layers.size(); }
    /** Public read-only access to the underlying layer list (used by Panama wire-up). */
    public List<TruthTableLayer> layers() { return layers; }
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
        if (useNative && tablesForNative != null) {
            return evaluateNative(input);
        }
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
            return new ChainResult(
                    boolArrFrom(evaluateForward(bitSetFrom(input)), 256),
                    0.0,  // weighted score (computed by evaluateWithMagnitude; backward compat)
                    0);
        } finally {
            totalNanos.addAndGet(System.nanoTime() - t0);
        }
    }

    /** Convert a boolean[] to a BitSet. */
    private static java.util.BitSet bitSetFrom(boolean[] input) {
        java.util.BitSet bs = new java.util.BitSet(input.length);
        for (int i = 0; i < input.length; i++) if (input[i]) bs.set(i);
        return bs;
    }

    /** Convert a BitSet to a boolean[] of the requested width (zero-padded). */
    private static boolean[] boolArrFrom(java.util.BitSet bs, int width) {
        boolean[] out = new boolean[Math.max(width, 1)];
        int limit = Math.min(bs.length(), width);
        // bs.length() returns highest set bit + 1, so use toLongArray() to walk all
        for (int i = bs.nextSetBit(0); i >= 0 && i < width; i = bs.nextSetBit(i + 1)) {
            out[i] = true;
        }
        return out;
    }

    /**
     * Run the chain forward through all layers, returning the output
     * BitSet of the final layer. Each layer consumes input
     * {@code neuronCount*k} bits and produces {@code neuronCount} bits
     * (one per neuron). Input is zero-padded on the right when shorter
     * than the layer needs; output is zero-trimmed when longer.
     *
     * <p>This replaces the previous implementation that called
     * {@code resize(state, neuronCount*k/2)} between layers, which
     * SHRANK the state below the next layer's input width and caused
     * most neurons to never fire.
     */
    public java.util.BitSet evaluateForward(java.util.BitSet inputState) {
        if (layers.isEmpty()) {
            // No layers — return input as-is
            java.util.BitSet out = new java.util.BitSet(inputState.length());
            for (int i = inputState.nextSetBit(0); i >= 0; i = inputState.nextSetBit(i + 1)) {
                out.set(i);
            }
            return out;
        }

        // Convert BitSet → boolean[] once, then operate in arrays for speed
        int maxLayerWidth = 0;
        for (TruthTableLayer layer : layers) {
            int w = layer.neuronCount() * layer.k();
            if (w > maxLayerWidth) maxLayerWidth = w;
        }
        // also ensure we capture all the input bits
        int initWidth = Math.max(maxLayerWidth, inputState.length());

        boolean[] state = boolArrFrom(inputState, initWidth);
        // Constrain state to bits set in inputState; rest stays false (padded).

        for (TruthTableLayer layer : layers) {
            int n = layer.neuronCount();
            int k = layer.k();
            int inputWidth = n * k;
            // Pad / truncate state to inputWidth
            if (state.length < inputWidth) {
                boolean[] padded = new boolean[inputWidth];
                System.arraycopy(state, 0, padded, 0, state.length);
                state = padded;
            } else if (state.length > inputWidth) {
                state = java.util.Arrays.copyOf(state, inputWidth);
            }

            // Evaluate each neuron
            boolean[] next = new boolean[n];
            for (int i = 0; i < n; i++) {
                TruthTable neuron = layer.neurons().get(i);
                int sliceStart = i * k;
                int cellIndex = 0;
                for (int j = 0; j < k; j++) {
                    if (state[sliceStart + j]) {
                        cellIndex |= (1 << j);
                    }
                }
                // skip if neuron has no table (defensive)
                if (neuron == null) continue;
                try {
                    next[i] = neuron.evaluate(cellIndex);
                } catch (Throwable t) {
                    next[i] = false;
                }
            }
            state = next;
        }

        // Convert back to BitSet
        java.util.BitSet out = new java.util.BitSet(state.length);
        for (int i = 0; i < state.length; i++) if (state[i]) out.set(i);
        return out;
    }

    /**
     * Run the chain forward with weighted-score tracking.
     * <p>Used by hash scoring in generation; returns magnitude of
     * activation (sum of set-bits weighted by per-neuron density).
     */
    public ChainResult evaluateWithMagnitude(boolean[] input) {
        evalCount.incrementAndGet();
        long t0 = System.nanoTime();
        try {
            if (layers.isEmpty()) {
                boolean[] out = new boolean[input.length];
                System.arraycopy(input, 0, out, 0, input.length);
                return new ChainResult(out, 0.0, 0);
            }
            java.util.BitSet state = bitSetFrom(input);
            double weightedSum = 0.0;
            int neuronsFired = 0;

            for (TruthTableLayer layer : layers) {
                int n = layer.neuronCount();
                int k = layer.k();
                int inputWidth = n * k;
                boolean[] in = boolArrFrom(state, inputWidth);
                boolean[] next = new boolean[n];
                for (int i = 0; i < n; i++) {
                    TruthTable neuron = layer.neurons().get(i);
                    int sliceStart = i * k;
                    int cellIndex = 0;
                    for (int j = 0; j < k; j++) {
                        if (in[sliceStart + j]) cellIndex |= (1 << j);
                    }
                    boolean fired = false;
                    try { fired = neuron.evaluate(cellIndex); }
                    catch (Throwable t) { /* defensive */ }
                    next[i] = fired;
                    if (fired) {
                        int cells = 1 << k;
                        double density = (double) neuron.table().cardinality() / cells;
                        weightedSum += 0.5 + density;
                        neuronsFired++;
                    }
                }
                // bitSetFrom-style: copy boolean[] state into a fresh BitSet for next layer
            {
                java.util.BitSet bs = new java.util.BitSet(next.length);
                for (int i = 0; i < next.length; i++) if (next[i]) bs.set(i);
                state = bs;
            }
            }
            boolean[] out = boolArrFrom(state, state.length());
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

    /** Fast path: Project Panama FFM call to libtruthy. Skips the
     * pure-Java BitSet loop entirely. Returns the layer's output
     * bits directly. Caller is responsible for wiring this to a
     * constructed runner with the {@code useNative} flag. */
    private boolean[] evaluateNative(boolean[] input) {
        if (tablesForNative == null) return evaluateWithScore(input).bits();
        // encode input bits as bytes (0/1 per bit)
        byte[] inBytes = new byte[input.length];
        for (int i = 0; i < input.length; i++) inBytes[i] = (byte) (input[i] ? 1 : 0);
        int totalNeurons = tablesForNative.size() * 64;  // approximate
        byte[] outBytes = new byte[Math.max(totalNeurons, 1)];
        // The native call expects a single table; for the multi-layer
        // case we call it per-layer. For now we only support single-
        // layer chains. Multi-layer FFM dispatch is a future phase.
        try {
            // Use the bridge if loaded; otherwise fall through to Java.
            if (panamaBridge != null && panamaBridge.isLoaded()) {
                // compute the per-layer offset in the output buffer
                int offset = 0;
                for (int i = 0; i < tablesForNative.size() && i < layers.size(); i++) {
                    long[] table = tablesForNative.get(i);
                    int neurons = Math.min(table.length * 64, layers.get(i).neuronCount());
                    byte[] layerOut = new byte[neurons];
                    int sliceLen = neurons * kForNative;
                    if (inBytes.length >= sliceLen) {
                        panamaBridge.evaluate(inBytes, neurons, kForNative, table, layerOut);
                    }
                    int copyLen = Math.min(neurons, outBytes.length - offset);
                    if (copyLen > 0) {
                        System.arraycopy(layerOut, 0, outBytes, offset, copyLen);
                    }
                    offset += neurons;
                    if (offset >= outBytes.length) break;
                }
            } else {
                // Bridge not loaded; just return the same path as Java.
                return evaluateWithScore(input).bits();
            }
        } catch (Throwable t) {
            // on any native failure, fall back to Java path
            return evaluateWithScore(input).bits();
        }
        boolean[] result2 = new boolean[outBytes.length];
        for (int i3 = 0; i3 < outBytes.length; i3++) result2[i3] = outBytes[i3] != 0;
        return result2;
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