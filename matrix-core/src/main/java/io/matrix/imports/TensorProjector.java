package io.matrix.imports;

import io.matrix.neuron.TruthTable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Projects a {@link SafetensorsReader.Tensor} (float[]) into one or more
 * {@link TruthTable} instances suitable for embedding into the Matrix FNL pool.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Normalise the tensor to {@code [-1..+1]} range (after min/max discovery).</li>
 *   <li>Pick the {@code k} with {@code 2^k} close to the desired neuron count
 *       (capped at {@link TruthTable#K_MAX=20}, i.e. 2^20 = 1 M entries).</li>
 *   <li>For each neuron, pack the surrounding {@code 2^k} projected values
 *       into a {@link TruthTable} by thresholding against {@code 0}.</li>
 * </ol>
 *
 * <p>Output neurons are tagged with the source tensor name so the FNL pool can
 * cite provenance (important for L7 ethics review of injected knowledge).
 *
 * <p>Ref: L1 §3.1 (TruthTable), L24 §2 (Weight Import).
 */
public final class TensorProjector {

    /** Projection budget: max total truth-table entries produced per tensor. */
    public static final int DEFAULT_BUDGET_ENTRIES = 1 << 16;  // 65_536 entries / tensor

    private final int budgetEntries;

    public TensorProjector() { this(DEFAULT_BUDGET_ENTRIES); }

    public TensorProjector(int budgetEntries) {
        if (budgetEntries <= 0) throw new IllegalArgumentException("budget must be > 0");
        this.budgetEntries = budgetEntries;
    }

    /** Result: a list of projected neurons along with provenance metadata. */
    public record Projection(
            String sourceTensor,
            String dtype,
            int[] sourceShape,
            long minValueBits,
            long maxValueBits,
            List<TruthTable> truthTables,
            int neuronCount) {

        /** Stable, human-readable summary for logs / Noosphere citations. */
        public String citation() {
            return String.format("import:%s/%s shape=%s neurons=%d k=%d",
                    sourceTensor, dtype, java.util.Arrays.toString(sourceShape),
                    neuronCount, truthTables.isEmpty() ? 0 : truthTables.get(0).k());
        }
    }

    /**
     * Projects a tensor into a {@link Projection}. The tensor data is consumed once
     * to derive per-neuron truth tables; we do not retain the float[] afterwards
     * to keep memory bounded.
     */
    public Projection project(SafetensorsReader.Tensor tensor) {
        if (tensor == null || tensor.data().length == 0) {
            return new Projection(tensor == null ? "?" : tensor.name(),
                    tensor == null ? "?" : tensor.dtype(),
                    tensor == null ? new int[0] : tensor.shape(),
                    0L, 0L, List.of(), 0);
        }
        float[] data = tensor.data();
        float min = tensor.min();
        float max = tensor.max();
        // Avoid dividing by zero for constant tensors — assign midpoint.
        float range = (max - min);
        double scale = (range == 0f) ? 1.0 : 2.0 / range;  // → maps to [-1, +1]
        double offset = -(max + min) / (range == 0f ? 1.0 : range) - 1.0;

        // Pick k = min(floor(log2(budget)), floor(log2(data.length)), K_MAX).
        // Each neuron spans 2^k input bits → total payload is neurons × 2^k = N
        // so we must have 2^k ≤ N; otherwise neurons < 1.
        int k = smallestKWithin(Math.min(budgetEntries, data.length));
        if (k > TruthTable.K_MAX) k = TruthTable.K_MAX;
        if (k < 1) k = 1;

        // Number of neurons = data.length / 2^k (rounded down)
        int size = 1 << k;
        int neuronCount = Math.max(1, data.length / size);
        List<TruthTable> tables = new ArrayList<>(neuronCount);
        for (int n = 0; n < neuronCount; n++) {
            int start = n * size;
            BitSet bits = new BitSet(size);
            for (int i = 0; i < size; i++) {
                int idx = start + i;
                if (idx >= data.length) break;
                double normalised = data[idx] * scale + offset;
                // Threshold at zero: output 1 if positive after normalisation.
                bits.set(i, normalised > 0.0);
            }
            tables.add(TruthTable.of(k, bits));
        }
        return new Projection(
                tensor.name(), tensor.dtype(), tensor.shape(),
                Float.floatToRawIntBits(min), Float.floatToRawIntBits(max),
                List.copyOf(tables),
                neuronCount);
    }

    /** Smallest {@code k ≥ 1} with {@code 2^k ≤ cap} and {@code 2^k ≤ 2^K_MAX=20}. */
    static int smallestKWithin(int cap) {
        if (cap <= 1) return 1;
        int k = 0;
        int probe = 1;
        while (probe <= cap && k < TruthTable.K_MAX) {
            probe <<= 1;
            k++;
        }
        // step back to the last valid k where 2^k <= cap
        return Math.max(1, k - 1);
    }
}
