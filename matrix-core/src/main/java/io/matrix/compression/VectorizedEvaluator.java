package io.matrix.compression;

import io.matrix.neuron.TruthTable;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * Boolean vector evaluation using Java Vector API ({@code jdk.incubator.vector}).
 *
 * <p>Uses {@link IntVector} operations to evaluate multiple truth table lookups
 * in parallel, leveraging CPU SIMD instructions (AVX2/AVX-512/NEON/SVE).
 *
 * <p>Performance target: &gt;2× faster than {@link SimdEvaluator} for batch
 * sizes ≥ 64 tables on AVX2-capable hardware.
 *
 * <p>Ref: L.5 — Performance Optimization (boolean vector SIMD)
 */
public final class VectorizedEvaluator {

    /** Preferred species: 256-bit integer vectors (8× int lanes on most CPUs). */
    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

    private static final int LANES = SPECIES.length();
    private static final int LANE_MASK = LANES - 1;

    private VectorizedEvaluator() {
    }

    /**
     * Evaluates a batch of truth tables on a batch of inputs using Vector API SIMD.
     *
     * <p>For each input index, evaluates all truth tables by performing
     * vectorized bit extraction across the truth table.
     *
     * @param tables truth tables (all must have the same k)
     * @param inputs packed input vectors
     * @return results[inputs.length][tables.size()]
     */
    public static boolean[][] batchEvaluate(List<TruthTable> tables, int[] inputs) {
        Objects.requireNonNull(tables, "tables");
        Objects.requireNonNull(inputs, "inputs");

        if (tables.isEmpty() || inputs.length == 0) {
            return new boolean[inputs.length][tables.size()];
        }

        int numTables = tables.size();
        int numInputs = inputs.length;
        boolean[][] results = new boolean[numInputs][numTables];

        int k = tables.get(0).k();
        int tableSize = 1 << k;

        // Pre-extract truth tables into dense int[] arrays for vector load
        int[][] tableInts = new int[numTables][tableSize];
        for (int t = 0; t < numTables; t++) {
            BitSet bs = tables.get(t).table();
            for (int i = 0; i < tableSize; i++) {
                tableInts[t][i] = bs.get(i) ? 1 : 0;
            }
        }

        // Vectorized processing: SIMD across tables for each input
        for (int i = 0; i < numInputs; i++) {
            int idx = inputs[i] & (tableSize - 1);
            evaluateInputVectorized(tableInts, idx, results[i]);
        }

        return results;
    }

    /**
     * Evaluates all tables for one input using vectorized gather.
     *
     * <p>Processes tables in SIMD lane-sized blocks: loads index for all
     * tables in the block, performs vectorized lookup, and stores results.
     */
    private static void evaluateInputVectorized(int[][] tableInts, int idx, boolean[] result) {
        int numTables = tableInts.length;

        int t = 0;
        for (; t + LANES <= numTables; t += LANES) {
            // Gather: load one element from each of LANE_COUNT tables
            int[] values = new int[LANES];
            for (int l = 0; l < LANES; l++) {
                values[l] = tableInts[t + l][idx];
            }

            // Vectorized compare: values != 0
            IntVector vec = IntVector.fromArray(SPECIES, values, 0);
            VectorMask<Integer> mask = vec.compare(VectorOperators.NE, 0);

            // Store results
            for (int l = 0; l < LANES; l++) {
                result[t + l] = mask.laneIsSet(l);
            }
        }

        // Tail: remaining tables
        for (; t < numTables; t++) {
            result[t] = tableInts[t][idx] != 0;
        }
    }

    /**
     * Evaluates a single truth table on multiple inputs using vectorized lookup.
     *
     * <p>Processes inputs in SIMD lane-sized blocks, loading input indices
     * and performing vectorized bit extraction.
     *
     * @param table  truth table
     * @param inputs packed input vectors
     * @return output bits
     */
    public static boolean[] batchEvaluateSingle(TruthTable table, int[] inputs) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(inputs, "inputs");

        int k = table.k();
        int tableSize = 1 << k;
        boolean[] results = new boolean[inputs.length];

        BitSet bs = table.table();
        int[] tableInts = new int[tableSize];
        for (int i = 0; i < tableSize; i++) {
            tableInts[i] = bs.get(i) ? 1 : 0;
        }

        int mask = tableSize - 1;

        int i = 0;
        for (; i + LANES <= inputs.length; i += LANES) {
            // Load LANE_COUNT indices
            int[] indices = new int[LANES];
            for (int l = 0; l < LANES; l++) {
                indices[l] = inputs[i + l] & mask;
            }

            // Gather and compare in one vector op
            int[] values = new int[LANES];
            for (int l = 0; l < LANES; l++) {
                values[l] = tableInts[indices[l]];
            }

            IntVector vec = IntVector.fromArray(SPECIES, values, 0);
            VectorMask<Integer> isSet = vec.compare(VectorOperators.NE, 0);

            for (int l = 0; l < LANES; l++) {
                results[i + l] = isSet.laneIsSet(l);
            }
        }

        // Tail
        for (; i < inputs.length; i++) {
            results[i] = tableInts[inputs[i] & mask] != 0;
        }

        return results;
    }

    /**
     * Returns the SIMD lane count for the preferred species.
     */
    public static int laneCount() {
        return LANES;
    }

    /**
     * Returns the preferred species bit size (e.g., 256 for AVX2).
     */
    public static int speciesBitSize() {
        return SPECIES.vectorBitSize();
    }

    /**
     * Returns the preferred species name for diagnostics.
     */
    public static String speciesInfo() {
        return String.format("VectorSpecies[%s, %d bits, %d lanes]",
                SPECIES.elementType().getSimpleName(),
                SPECIES.vectorBitSize(),
                SPECIES.length());
    }
}
