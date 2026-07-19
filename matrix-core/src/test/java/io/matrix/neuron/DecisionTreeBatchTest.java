package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionTreeBatchTest {

    @Test
    void evaluateAll64ShouldMatchPerCall() {
        // Build: out = bit 0 AND bit 1
        // leftChild = bit0=false → Leaf(false) (fast-fail)
        // rightChild = bit0=true → Split(1, leftChild=false, rightChild=true)
        var dt = new DecisionTree.Split(0,
                new DecisionTree.Leaf(false),       // bit 0 = false → false
                new DecisionTree.Split(1,           // bit 0 = true → check bit 1
                        new DecisionTree.Leaf(false),
                        new DecisionTree.Leaf(true)));
        BitSet[] inputs = new BitSet[8];
        for (int i = 0; i < 8; i++) {
            BitSet bs = new BitSet(8);
            if ((i & 1) != 0) bs.set(0);
            if ((i & 2) != 0) bs.set(1);
            inputs[i] = bs;
        }
        long packed = DecisionTreeBatch.evaluateAll64(dt, inputs);
        long expected = 0L;
        for (int i = 0; i < inputs.length; i++) {
            if (dt.evaluate(inputs[i])) expected |= (1L << i);
        }
        assertThat(packed).isEqualTo(expected);
        // Only input 3 (bits 0 and 1 set) is true → bit 3 set
        assertThat(packed & 0b1000L).isNotZero();
    }

    @Test
    void trueCountShouldMatchBitSetPopulation() {
        // out = bit 0 (just an identity)
        var dt = new DecisionTree.Split(0,
                new DecisionTree.Leaf(false),
                new DecisionTree.Leaf(true));
        BitSet[] inputs = new BitSet[16];
        for (int i = 0; i < 16; i++) {
            BitSet bs = new BitSet(4);
            if (i % 3 == 0) bs.set(0);
            inputs[i] = bs;
        }
        int n = DecisionTreeBatch.trueCount(dt, inputs);
        long packed = DecisionTreeBatch.evaluateAll64(dt, inputs);
        assertThat(n).isEqualTo(Long.bitCount(packed));
        // Every input where i % 3 == 0 → bit 0 set → true
        // Indices 0, 3, 6, 9, 12, 15 → 6 inputs
        assertThat(n).isEqualTo(6);
    }

    @Test
    void shouldRejectNulls() {
        assertThatThrownBy(() -> DecisionTreeBatch.evaluateAll64(null, new BitSet[0]))
                .isInstanceOf(IllegalArgumentException.class);
        var dt = new DecisionTree.Leaf(false);
        assertThatThrownBy(() -> DecisionTreeBatch.evaluateAll64(dt, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMoreThan64Inputs() {
        var dt = new DecisionTree.Leaf(true);
        BitSet[] inputs = new BitSet[65];
        assertThatThrownBy(() -> DecisionTreeBatch.evaluateAll64(dt, inputs))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evaluateAll64FromLongsShouldMatchPerCall() {
        // Same AND tree as above
        var dt = new DecisionTree.Split(0,
                new DecisionTree.Leaf(false),
                new DecisionTree.Split(1,
                        new DecisionTree.Leaf(false),
                        new DecisionTree.Leaf(true)));
        long[][] words = new long[8][1];
        for (int i = 0; i < 8; i++) {
            if ((i & 1) != 0) words[i][0] |= (1L << 0);
            if ((i & 2) != 0) words[i][0] |= (1L << 1);
        }
        long packed = DecisionTreeBatch.evaluateAll64FromLongs(dt, words, 2);
        assertThat(packed & 0b1000L).isNotZero();
        assertThat(packed & 0b0111L).isZero();
    }

    @Test
    void shouldRejectInvalidK() {
        var dt = new DecisionTree.Leaf(false);
        assertThatThrownBy(() -> DecisionTreeBatch.evaluateAll64FromLongs(dt, new long[1][1], 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DecisionTreeBatch.evaluateAll64FromLongs(dt, new long[1][1], 64))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyInputArrayShouldReturnZero() {
        var dt = new DecisionTree.Leaf(true);
        assertThat(DecisionTreeBatch.evaluateAll64(dt, new BitSet[0])).isZero();
    }
}
