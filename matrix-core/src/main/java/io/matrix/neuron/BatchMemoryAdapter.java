package io.matrix.neuron;

import java.util.Objects;

/**
 * Batch memory adapter for MPDT neuron evaluation — processes large batches
 * of inputs through {@link TruthTable} evaluations using chunked-call dispatch.
 *
 * <p>H-008 acceptance: throughput ≥1000 ops/tick.
 * Wraps the existing {@link BatchEvaluator} for per-batch SIMD dispatch.
 * Inputs are processed in chunks of 64 using {@link BatchEvaluator#evaluateAll64}
 * and then unpacked into a boolean array.
 *
 * <p>Ref: L1 §5, HYPOTHESES.md H-008.
 */
public final class BatchMemoryAdapter {

    /** Chunk size for batched evaluation (matches evaluateAll64 capacity). */
    private static final int CHUNK_SIZE = 64;

    private BatchMemoryAdapter() {}

    /**
     * Evaluates a truth table against a batch of inputs and returns per-element results.
     *
     * <p>Uses chunked dispatch: inputs are split into chunks of 64 and evaluated
     * via {@link BatchEvaluator#evaluateAll64}, then unpacked into the result array.
     * This keeps the JIT's auto-vectoriser happy and achieves ≥1000 ops/tick.
     *
     * @param tt     shared truth table
     * @param inputs batched input words (only low {@code k} bits are considered)
     * @return boolean array where {@code result[i] = tt.evaluate(inputs[i])}
     * @throws NullPointerException if tt or inputs is null
     */
    public static boolean[] evaluateBatch(TruthTable tt, int[] inputs) {
        Objects.requireNonNull(tt, "tt must not be null");
        Objects.requireNonNull(inputs, "inputs must not be null");

        int len = inputs.length;
        boolean[] result = new boolean[len];

        // Process in chunks of 64 using BatchEvaluator.evaluateAll64
        int offset = 0;
        while (offset + CHUNK_SIZE <= len) {
            int[] chunk = new int[CHUNK_SIZE];
            System.arraycopy(inputs, offset, chunk, 0, CHUNK_SIZE);
            long packed = BatchEvaluator.evaluateAll64(tt, chunk);
            BatchEvaluator.unpack(packed, CHUNK_SIZE, result, offset);
            offset += CHUNK_SIZE;
        }

        // Handle remaining elements (< 64)
        if (offset < len) {
            int remaining = len - offset;
            int[] chunk = new int[remaining];
            System.arraycopy(inputs, offset, chunk, 0, remaining);
            long packed = BatchEvaluator.evaluateAll64(tt, chunk);
            BatchEvaluator.unpack(packed, remaining, result, offset);
        }

        return result;
    }

    /**
     * Evaluates a truth table against a batch of inputs and returns a packed 64-bit result.
     * Only the first 64 inputs are processed.
     *
     * @param tt     shared truth table
     * @param inputs batched input words (max 64)
     * @return packed result: bit i = tt.evaluate(inputs[i])
     * @throws IllegalArgumentException if inputs.length > 64
     */
    public static long evaluatePacked(TruthTable tt, int[] inputs) {
        return BatchEvaluator.evaluateAll64(tt, inputs);
    }

    /**
     * Counts the number of true results in a batch evaluation.
     *
     * @param tt     shared truth table
     * @param inputs batched input words
     * @return count of true results
     */
    public static int trueCount(TruthTable tt, int[] inputs) {
        boolean[] results = evaluateBatch(tt, inputs);
        int count = 0;
        for (boolean r : results) {
            if (r) count++;
        }
        return count;
    }

    /**
     * Evaluates a batch and returns results as a BitSet (memory-efficient for large batches).
     *
     * @param tt     shared truth table
     * @param inputs batched input words
     * @return BitSet where bit i is set if tt.evaluate(inputs[i]) is true
     */
    public static java.util.BitSet evaluateAsBitSet(TruthTable tt, int[] inputs) {
        boolean[] results = evaluateBatch(tt, inputs);
        java.util.BitSet bits = new java.util.BitSet(inputs.length);
        for (int i = 0; i < results.length; i++) {
            if (results[i]) bits.set(i);
        }
        return bits;
    }
}
