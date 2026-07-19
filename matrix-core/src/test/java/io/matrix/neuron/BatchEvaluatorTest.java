package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchEvaluatorTest {

    @Test
    void evaluateAll64ShouldMatchPerElementEvaluate() {
        BitSet tt = new BitSet(64);
        tt.set(0); tt.set(5); tt.set(10); tt.set(31); tt.set(63);
        TruthTable truth = TruthTable.of(6, tt);
        int[] inputs = new int[64];
        for (int i = 0; i < 64; i++) inputs[i] = i;

        long packed = BatchEvaluator.evaluateAll64(truth, inputs);
        long expected = 0L;
        for (int i = 0; i < 64; i++) {
            if (truth.evaluate(inputs[i])) expected |= (1L << i);
        }
        assertThat(packed).isEqualTo(expected);
    }

    @Test
    void evaluateAll32ShouldStayWithinInt() {
        BitSet tt = new BitSet(32);
        tt.set(3, 8);   // bits 3..7
        TruthTable truth = TruthTable.of(5, tt);
        int[] inputs = {0, 3, 7, 8, 12, 31};
        int r = BatchEvaluator.evaluateAll32(truth, inputs);
        assertThat(Long.bitCount(r & 0xFFFFFFFFL)).isEqualTo(2); // 3 and 7 → true
    }

    @Test
    void trueCountShouldMatchBitSetCardinality() {
        TruthTable tt = TruthTable.of(4, trueCountHelper());
        int[] inputs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        long packed = BatchEvaluator.evaluateAll64(tt, inputs);
        // For k=4, TruthTable has all even-indexed outputs set (count=8).
        assertThat(Long.bitCount(packed)).isEqualTo(8);
    }

    private BitSet trueCountHelper() {
        BitSet tt = new BitSet(16);
        for (int i = 0; i < 16; i += 2) tt.set(i);
        return tt;
    }

    @Test
    void evaluateAll64ShouldRejectInputsOver64() {
        TruthTable tt = TruthTable.of(2, new BitSet(4));
        assertThatThrownBy(() -> BatchEvaluator.evaluateAll64(tt, new int[65]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evaluateAll32ShouldRejectInputsOver32() {
        TruthTable tt = TruthTable.of(2, new BitSet(4));
        assertThatThrownBy(() -> BatchEvaluator.evaluateAll32(tt, new int[33]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sumInputsShouldMaskToKBits() {
        int[] inputs = {0b11111111, 0b00010000, 0b10101010};
        assertThat(BatchEvaluator.sumInputs(inputs, 4))
                .isEqualTo(15 + 0 + 10);   // masked to 4-bit each
    }

    @Test
    void unpackShouldRestoreBooleanArray() {
        long packed = 0b1010L;   // bits 1 and 3 set
        boolean[] out = BatchEvaluator.unpack(packed, 4);
        assertThat(out).containsExactly(false, true, false, true);
    }

    @Test
    void packInputsShouldCopyBitsIntoInts() {
        BitSet bs = new BitSet(8);
        bs.set(0); bs.set(3); bs.set(7);
        int[] arr = BatchEvaluator.packInputs(bs, 8);
        assertThat(arr).containsExactly(1, 0, 0, 1, 0, 0, 0, 1);
    }
}
