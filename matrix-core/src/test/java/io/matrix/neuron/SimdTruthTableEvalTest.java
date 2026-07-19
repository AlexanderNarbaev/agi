package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimdTruthTableEvalTest {

    @Test
    void evaluateSingleLongShouldMatchReference() {
        // Build table where bit i is set iff (i % 2 == 1)
        long tableWord = 0L;
        for (int i = 0; i < 64; i++) {
            if ((i & 1) != 0) tableWord |= (1L << i);
        }
        int[] inputs = new int[64];
        for (int i = 0; i < 64; i++) inputs[i] = i;
        long packed = SimdTruthTableEval.evaluateSingleLong(tableWord, inputs);
        long expected = 0L;
        for (int i = 0; i < 64; i++) {
            if (((tableWord >>> i) & 1L) != 0L) expected |= (1L << i);
        }
        assertThat(packed).isEqualTo(expected);
    }

    @Test
    void evaluateSingleLongShouldHandleEmptyBatch() {
        assertThat(SimdTruthTableEval.evaluateSingleLong(0xFFFFFFFFL, new int[0])).isZero();
    }

    @Test
    void evaluateAll64ShouldFallThroughForLargeK() {
        // For k > 6, evaluateAll64 uses BatchEvaluator path
        BitSet big = new BitSet(1 << 7);  // k = 7
        for (int i = 0; i < 128; i += 2) big.set(i);
        TruthTable tt = TruthTable.of(7, big);
        int[] inputs = new int[10];
        for (int i = 0; i < 10; i++) inputs[i] = i;
        long packed = SimdTruthTableEval.evaluateAll64(tt, inputs);
        long expected = 0L;
        for (int i = 0; i < 10; i++) {
            if (tt.evaluate(inputs[i])) expected |= (1L << i);
        }
        assertThat(packed).isEqualTo(expected);
    }

    @Test
    void evaluateAll64ShouldMatchReferenceForK6() {
        BitSet bits = new BitSet(64);
        Random rng = new Random(42L);
        for (int i = 0; i < 64; i++) if (rng.nextBoolean()) bits.set(i);
        TruthTable tt = TruthTable.of(6, bits);
        int[] inputs = new int[64];
        for (int i = 0; i < 64; i++) inputs[i] = i;
        long simd = SimdTruthTableEval.evaluateAll64(tt, inputs);
        long ref = BatchEvaluator.evaluateAll64(tt, inputs);
        assertThat(simd).isEqualTo(ref);
    }

    @Test
    void shouldRejectNull() {
        assertThatThrownBy(() -> SimdTruthTableEval.evaluateSingleLong(0L, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectMoreThan64Inputs() {
        assertThatThrownBy(() -> SimdTruthTableEval.evaluateSingleLong(0L, new int[65]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
