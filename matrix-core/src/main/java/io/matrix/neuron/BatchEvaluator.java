package io.matrix.neuron;

import java.util.BitSet;
import java.util.Objects;

/**
 * SIMD-friendly batch evaluator for {@link TruthTable}.
 *
 * <p>The hot path of {@code TruthTable.evaluate(int)} reduces to a single bit
 * extraction from the underlying {@code tableLongs} array whenever the index
 * fits in a long (always for {@code k ≤ 6}). For larger {@code k}, evaluation
 * iterates the backing {@code long[]} array. Both code paths are reachable
 * from a single helper here so downstream consumers (the agent loop, MPDT
 * neuron clusters) can swap single-call evaluation for vector-style batch
 * evaluation cheaply.
 *
 * <p>The {@code evaluateAll64} method processes a 64-element input batch
 * using a bit-shift OR-reduction: every output is computed via a single
 * mask shift and then OR-ed together to recover a 64-bit packed result,
 * which the caller then unpacks with {@code Long.bitCount} / bit tests.
 * This keeps the JVM's auto-vectoriser happy in tight loops (the same trick
 * is used in {@code java.util.BitSet#nextSetBit}).
 *
 * <p>Ref: L1 §5, L23_Benchmark.md.
 */
public final class BatchEvaluator {

    private BatchEvaluator() {}

    /**
     * Evaluates the truth table for every input in {@code inputs}.
     *
     * @param tt     shared truth table
     * @param inputs batched input words (only low {@code k} bits are considered)
     * @return packed 64-bit result: bit {@code i} = {@code tt.evaluate(inputs[i])}
     */
    public static long evaluateAll64(TruthTable tt, int[] inputs) {
        Objects.requireNonNull(tt, "tt");
        Objects.requireNonNull(inputs, "inputs");
        if (inputs.length > 64) {
            throw new IllegalArgumentException("evaluateAll64 supports up to 64 inputs per call");
        }
        long result = 0L;
        // Direct call to per-element evaluate keeps semantics aligned with the
        // single-input path; the JIT will auto-vectorise tight loops.
        for (int i = 0; i < inputs.length; i++) {
            if (tt.evaluate(inputs[i])) {
                result |= (1L << i);
            }
        }
        return result;
    }

    /**
     * Evaluates a smaller batch (≤32 inputs) into an int return value
     * (result bit {@code i} = {@code tt.evaluate(inputs[i])}). Useful
     * when the consumer wants a 32-bit-wide result (e.g. embedding into
     * another neuron).
     */
    public static int evaluateAll32(TruthTable tt, int[] inputs) {
        if (inputs.length > 32) {
            throw new IllegalArgumentException("evaluateAll32 supports up to 32 inputs per call");
        }
        long r = evaluateAll64(tt, inputs);
        return (int) r;
    }

    /**
     * Counts the {@code true} outputs over a batch (Monte-Carlo style).
     * Equivalent to {@code Long.bitCount(evaluateAll64(tt, inputs))}.
     */
    public static int trueCount(TruthTable tt, int[] inputs) {
        return Long.bitCount(evaluateAll64(tt, inputs));
    }

    /**
     * Sum of all input words restricted to {@code k} bits.
     * Used by DecisionTree for fast histogram-style gates.
     */
    public static int sumInputs(int[] inputs, int k) {
        long mask = k >= 64 ? -1L : (1L << k) - 1L;
        int sum = 0;
        for (int i = 0; i < inputs.length; i++) sum += (int) (inputs[i] & mask);
        return sum;
    }

    /** Convenience: convert a packed 64-bit result back into a {@code boolean[]}. */
    public static boolean[] unpack(long packed, int length) {
        boolean[] out = new boolean[length];
        for (int i = 0; i < length; i++) out[i] = ((packed >>> i) & 1L) != 0L;
        return out;
    }

    /**
     * Convenience: convert a {@link BitSet} of length {@code size} into
     * an {@code int[]} of decision-tree-friendly words (LSB-first).
     */
    public static int[] packInputs(BitSet bits, int size) {
        int[] out = new int[size];
        for (int i = 0; i < size; i++) out[i] = bits.get(i) ? 1 : 0;
        return out;
    }
}

