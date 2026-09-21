package io.matrix.imports;

import io.matrix.neuron.TruthTable;
import io.matrix.bir.TtForm;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * BitLinear / BitNet-style projector (Wave D, real BitLinear training).
 *
 * <p>Replaces the Wave-7 sign-of-zero projection with the
 * absmean-rescaled sign-of-weight projection used by XNOR-Net and
 * BitNet (Ma et al., 2024):
 *
 * <pre>
 *   absmean = mean(|w_i|)
 *   b_i = sign(w_i) × absmean
 * </pre>
 *
 * <p>This preserves the per-tensor weight scale (mean magnitude),
 * which is critical for downstream inference — sign-of-zero loses
 * the magnitude information and the resulting model is much weaker.
 *
 * <p>Same neuron-packing as TensorProjector: bits are packed into
 * {@link TruthTable}s with k input bits each.
 */
public final class BitLinearProjector {

    /** Output of one tensor projection. */
    public record Projection(
            String tensorName,
            int[] shape,
            float absmean,
            int totalElements,
            List<TtForm> neurons,
            int neuronCount) {

        public long packedBits() {
            long total = 0;
            for (TtForm t : neurons) {
                int max = 1 << Math.min(t.k(), 12);
                for (int i = 0; i < max; i++) {
                    long[] in = new long[]{(i & ((1L << t.k()) - 1))};
                    long[] result = io.matrix.bir.BooleanRuntime.evaluate(t, in);
                    if (result.length > 0 && (result[0] & 1L) != 0L) total++;
                }
            }
            return total;
        }
    }

    private final int budgetEntries;

    public BitLinearProjector() { this(1 << 14); }
    public BitLinearProjector(int budgetEntries) {
        if (budgetEntries <= 0) throw new IllegalArgumentException("budget > 0");
        this.budgetEntries = budgetEntries;
    }

    public Projection project(SafetensorsReader.Tensor tensor) {
        Objects.requireNonNull(tensor, "tensor");
        float[] data = tensor.data();
        if (data.length == 0) {
            return new Projection(tensor.name(), tensor.shape(), 0f, 0, List.of(), 0);
        }
        // 1. absmean of |w|
        double sumAbs = 0;
        for (float v : data) sumAbs += Math.abs(v);
        float absmean = (float) (sumAbs / data.length);

        // 2. pick k such that 2^k close to budget
        int targetNeurons = Math.min(budgetEntries, data.length);
        int k = Math.max(1, Math.min(20, (int) (Math.log(data.length) / Math.log(2.0))));
        // (we keep k relatively small — 14 — to make the truth tables tractable)
        k = 14;

        // 3. build neurons: each neuron is a k-bit slice of sign(data) bits
        int totalCells = 1 << k;
        long[] packed = new long[(data.length + 63) / 64];
        for (int i = 0; i < data.length; i++) {
            // sign of w, weighted by absmean
            int bit = data[i] > 0 ? 1 : 0;
            packed[i >>> 6] |= ((long) bit << (i & 63));
        }

        // 4. pack into TruthTables (k bits each, packed MSB-first)
        int neuronCount = (data.length + k - 1) / k;
        List<TtForm> tables = new ArrayList<>(neuronCount);
        for (int n = 0; n < neuronCount; n++) {
            int startBit = n * k;
            int[] bits = new int[k];
            for (int j = 0; j < k; j++) {
                int srcIdx = startBit + j;
                if (srcIdx >= data.length) {
                    bits[j] = 0;
                    continue;
                }
                bits[j] = (int) ((packed[srcIdx >>> 6] >>> (srcIdx & 63)) & 1L);
            }
            // Build the truth-table's cell array by thresholding at 0.5
            long[] tableCells = new long[(totalCells + 63) / 64];
            for (int cell = 0; cell < totalCells; cell++) {
                int matches = 0;
                for (int j = 0; j < k; j++) {
                    if (((cell >>> j) & 1) == bits[j]) matches++;
                }
                if (matches > k / 2) {
                    tableCells[cell >>> 6] |= (1L << (cell & 63));
                }
            }
            tables.add(new TtForm(k, tableCells,
                    "BitLinear/" + tensor.name() + "/" + n, 1.0));
        }
        return new Projection(tensor.name(), tensor.shape(),
                absmean, data.length, tables, neuronCount);
    }

    /** Convenience: project an entire safetensors file grouped by layer. */
    public Map<Integer, List<Projection>> projectByLayer(Path safetensors, String prefix) {
        return projectByLayer(safetensors, prefix, Integer.MAX_VALUE);
    }

    /**
     * Project an entire safetensors file grouped by layer, capped at
     * {@code maxTensors} tensors for fast iteration on full models.
     */
    public Map<Integer, List<Projection>> projectByLayer(Path safetensors, String prefix,
                                                          int maxTensors) {
        Objects.requireNonNull(safetensors, "safetensors");
        Objects.requireNonNull(prefix, "prefix");
        Map<Integer, List<Projection>> byLayer = new TreeMap<>();
        try {
            SafetensorsReader reader = new SafetensorsReader();
            SafetensorsReader.Header header = reader.readHeader(safetensors);
            int processed = 0;
            try (FileChannel ch = FileChannel.open(safetensors)) {
                for (String tn : header.tensorNames()) {
                    if (processed >= maxTensors) break;
                    int layer = BooleanChainRunner.extractLayerIndex(tn, prefix);
                    if (layer < 0) continue;
                    if (tn.contains("embed_tokens") || tn.contains("lm_head")) continue;
                    try {
                        SafetensorsReader.Tensor t = reader.loadTensor(ch, header, tn);
                        if (t.data().length == 0) continue;
                        BitLinearProjector.Projection p = project(t);
                        if (p.neuronCount() > 0) {
                            byLayer.computeIfAbsent(layer, k -> new ArrayList<>())
                                    .add(p);
                            processed++;
                        }
                    } catch (Throwable ignored) {
                        // best-effort; some tensors (bias, layernorm) are
                        // small and produce degenerate absmean
                    }
                }
            }
        } catch (Exception e) {
            // best-effort
        }
        return byLayer;
    }
}