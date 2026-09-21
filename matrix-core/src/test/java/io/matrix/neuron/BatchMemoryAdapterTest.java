package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ISOLATED Unit Test for BatchMemoryAdapter
 * Target: matrix-core/src/main/java/io/matrix/neuron/BatchMemoryAdapter.java
 * Session: ses_6
 *
 * Tests: correctness vs per-element baseline +
 * throughput measurement for H-008 (≥1000 ops/tick target).
 */
class BatchMemoryAdapterTest {

    private static final int K = 6; // fits in single long — SIMD-able

    @Test
    void evaluateBatchShouldMatchPerElementBaseline() {
        TruthTable tt = randomTruthTable(K, 42);
        int[] inputs = generateInputs(100, K);

        boolean[] batchResult = BatchMemoryAdapter.evaluateBatch(tt, inputs);
        boolean[] baseline = evaluateBaseline(tt, inputs);

        assertThat(batchResult).containsExactly(baseline);
    }

    @Test
    void evaluateBatchLargeShouldMatchPerElementBaseline() {
        TruthTable tt = randomTruthTable(K, 123);
        int[] inputs = generateInputs(1024, K);

        boolean[] batchResult = BatchMemoryAdapter.evaluateBatch(tt, inputs);
        boolean[] baseline = evaluateBaseline(tt, inputs);

        assertThat(batchResult).containsExactly(baseline);
    }

    @Test
    void evaluateBatchWithK12ShouldMatchPerElementBaseline() {
        TruthTable tt = randomTruthTable(10, 77);
        int[] inputs = generateInputs(256, 10);

        boolean[] batchResult = BatchMemoryAdapter.evaluateBatch(tt, inputs);
        boolean[] baseline = evaluateBaseline(tt, inputs);

        assertThat(batchResult).containsExactly(baseline);
    }

    @Test
    void evaluateBatchEmptyShouldReturnEmpty() {
        TruthTable tt = randomTruthTable(K, 99);
        boolean[] result = BatchMemoryAdapter.evaluateBatch(tt, new int[0]);
        assertThat(result).isEmpty();
    }

    @Test
    void throughputShouldExceed1000OpsPerTick() {
        TruthTable tt = randomTruthTable(K, 0xDEAD);
        int[] inputs = generateInputs(10_000, K);

        // Warm-up
        for (int w = 0; w < 5; w++) {
            BatchMemoryAdapter.evaluateBatch(tt, inputs);
        }

        // Measure
        long start = System.nanoTime();
        BatchMemoryAdapter.evaluateBatch(tt, inputs);
        long elapsedNs = System.nanoTime() - start;

        double opsPerSec = inputs.length / (elapsedNs / 1_000_000_000.0);
        System.out.printf("BatchMemoryAdapter throughput: %.0f ops/sec (%d inputs in %.3f ms)%n",
                opsPerSec, inputs.length, elapsedNs / 1_000_000.0);

        // H-008: ≥1000 ops/tick (~1M ops/sec at 1000 ticks/sec)
        assertThat(opsPerSec).isGreaterThanOrEqualTo(1000.0);
    }

    @Test
    void evaluateBatchWithSingleInputShouldMatchDirectEval() {
        TruthTable tt = randomTruthTable(K, 55);
        int single = 42 & ((1 << K) - 1);

        boolean batchResult = BatchMemoryAdapter.evaluateBatch(tt, new int[]{single})[0];
        boolean directResult = tt.evaluate(single);

        assertThat(batchResult).isEqualTo(directResult);
    }

    // --- helpers ---

    private static TruthTable randomTruthTable(int k, long seed) {
        Random rng = new Random(seed);
        return TruthTable.random(k, rng);
    }

    private static int[] generateInputs(int count, int k) {
        Random rng = new Random(0xCAFE);
        int mask = (1 << k) - 1;
        int[] inputs = new int[count];
        for (int i = 0; i < count; i++) {
            inputs[i] = rng.nextInt() & mask;
        }
        return inputs;
    }

    private static boolean[] evaluateBaseline(TruthTable tt, int[] inputs) {
        boolean[] result = new boolean[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            result[i] = tt.evaluate(inputs[i]);
        }
        return result;
    }
}
