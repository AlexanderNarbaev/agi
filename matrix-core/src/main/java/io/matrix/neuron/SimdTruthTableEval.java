package io.matrix.neuron;

import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * Real SIMD evaluation of {@link TruthTable} via the JDK incubator vector API
 * ({@code jdk.incubator.vector}). Enabled by {@code --add-modules=jdk.incubator.vector}.
 *
 * <p>Strategy: treat the truth-table bits as a {@link LongVector} lane and the
 * batch of input indices as another lane, then perform
 * {@code tableLongShift = LongVector.SPECIES_PREFERRED.broadcast(tableWord)
 * .lanewise(shift, indices)}.
 *
 * <p>For a {@code k ≤ 6} truth table (entire table fits in one long word), this
 * gives a single vectorised {@code shift} + {@code AND 1} per batch element.
 *
 * <p>Ref: L1 §5 (performance), L23_Benchmark.md.
 */
public final class SimdTruthTableEval {

    private static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_PREFERRED;

    private SimdTruthTableEval() {}

    /**
     * SIMD-accelerated batch evaluation of a single-long truth table
     * (k ≤ 6) over a batch of input indices.
     *
     * @param tableWord the single long word containing 2^k output bits (LSB = index 0)
     * @param inputs    batched input words; only low {@code k} bits are considered
     * @return packed 64-bit result: bit {@code i} = {@code (tableWord >> inputs[i]) & 1}
     */
    public static long evaluateSingleLong(long tableWord, int[] inputs) {
        if (inputs == null) throw new NullPointerException("inputs");
        if (inputs.length > 64) {
            throw new IllegalArgumentException("evaluateSingleLong supports up to 64 inputs");
        }
        int n = inputs.length;
        int laneWidth = SPECIES.length();
        LongVector tableVec = LongVector.broadcast(SPECIES, tableWord);
        LongVector maskVec = LongVector.broadcast(SPECIES, 1L);
        long packed = 0L;
        int[] chunk = new int[laneWidth];
        long[] chunkLong = new long[laneWidth];
        int processed = 0;
        while (processed < n) {
            int chunkSize = Math.min(laneWidth, n - processed);
            for (int i = 0; i < chunkSize; i++) chunk[i] = inputs[processed + i];
            for (int i = chunkSize; i < laneWidth; i++) chunk[i] = 0;
            for (int i = 0; i < chunkSize; i++) chunkLong[i] = chunk[i] & 0xFFFFFFFFL;
            for (int i = chunkSize; i < laneWidth; i++) chunkLong[i] = 0;
            LongVector idxVec = LongVector.fromArray(SPECIES, chunkLong, 0);
            LongVector shifted = tableVec.lanewise(VectorOperators.LSHR, idxVec);
            LongVector masked = shifted.lanewise(VectorOperators.AND, maskVec);
            long[] out = masked.toLongArray();
            for (int i = 0; i < chunkSize; i++) {
                if (out[i] != 0) packed |= (1L << (processed + i));
            }
            processed += chunkSize;
        }
        return packed;
    }

    /** Convenience: SIMD wrapper around {@link BatchEvaluator#evaluateAll64} for
     * tables with k ≤ 6 (single-long representation). Falls back to
     * {@link BatchEvaluator} for larger tables.
     */
    public static long evaluateAll64(TruthTable tt, int[] inputs) {
        if (tt.k() <= 6) {
            long[] longs = tt.table().toLongArray();
            if (longs.length > 0) {
                return evaluateSingleLong(longs[0], inputs);
            }
        }
        return BatchEvaluator.evaluateAll64(tt, inputs);
    }
}
