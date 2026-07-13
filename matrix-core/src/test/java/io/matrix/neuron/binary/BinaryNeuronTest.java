package io.matrix.neuron.binary;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinaryNeuronTest {

    private final Random rng = new Random(42);

    // ─── Construction ───

    @Test
    void shouldCreateWithValidParameters() {
        BinaryNeuron neuron = new BinaryNeuron(8, 4, rng);
        assertThat(neuron.inputSize()).isEqualTo(8);
        assertThat(neuron.threshold()).isEqualTo(4);
    }

    @Test
    void shouldCreateWithDefaultThreshold() {
        BinaryNeuron neuron = new BinaryNeuron(8, rng);
        assertThat(neuron.threshold()).isEqualTo(4); // ceil(8/2) = 4
    }

    @Test
    void shouldCreateWithOddInputSize() {
        BinaryNeuron neuron = new BinaryNeuron(7, rng);
        assertThat(neuron.threshold()).isEqualTo(4); // ceil(7/2) = 4
    }

    @Test
    void shouldRejectInvalidInputSize() {
        assertThatThrownBy(() -> new BinaryNeuron(0, rng))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BinaryNeuron(-1, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidThreshold() {
        assertThatThrownBy(() -> new BinaryNeuron(8, -1, rng))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BinaryNeuron(8, 9, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCreateWithExplicitHiddenWeights() {
        int[] hw = {1, 0, 1, 0, 1, 0, 1, 0};
        BinaryNeuron neuron = new BinaryNeuron(8, 4, hw);
        assertThat(neuron.inputSize()).isEqualTo(8);
        assertThat(neuron.threshold()).isEqualTo(4);
    }

    @Test
    void shouldRejectMismatchedHiddenWeights() {
        int[] hw = {1, 0, 1};
        assertThatThrownBy(() -> new BinaryNeuron(8, 4, hw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── XNOR Activation ───

    @Test
    void xnorShouldMatchWhenInputEqualsWeights() {
        // Create neuron with known weights: all 1s
        int[] hw = {1, 1, 1, 1, 1, 1, 1, 1};
        BinaryNeuron neuron = new BinaryNeuron(8, 4, hw);

        // Input = all 1s → XNOR = all 1s → popcount = 8 → >= threshold(4) → 1
        BitSet input = allSet(8);
        assertThat(neuron.forward(input)).isEqualTo(1);
    }

    @Test
    void xnorShouldMismatchWhenInputOpposesWeights() {
        // Create neuron with known weights: all 1s
        int[] hw = {1, 1, 1, 1, 1, 1, 1, 1};
        BinaryNeuron neuron = new BinaryNeuron(8, 4, hw);

        // Input = all 0s → XNOR = all 0s → popcount = 0 → < threshold(4) → 0
        BitSet input = new BitSet(8);
        assertThat(neuron.forward(input)).isEqualTo(0);
    }

    @Test
    void xnorPartialMatch() {
        // Weights: 11110000
        int[] hw = {1, 1, 1, 1, 0, 0, 0, 0};
        BinaryNeuron neuron = new BinaryNeuron(8, 4, hw);

        // Input: 11110000 → XNOR = 11111111 → popcount = 8 → >= 4 → 1
        BitSet input = new BitSet(8);
        input.set(0, 4); // bits 0-3 set
        assertThat(neuron.forward(input)).isEqualTo(1);

        // Input: 00001111 → XNOR with 11110000 → 00000000 → popcount = 0 → < 4 → 0
        BitSet input2 = new BitSet(8);
        input2.set(4, 8); // bits 4-7 set
        assertThat(neuron.forward(input2)).isEqualTo(0);

        // Input: 10101010 (bits 1,3,5,7) → XNOR with 11110000
        // bit0: XNOR(0,1)=0, bit1: XNOR(1,1)=1, bit2: XNOR(0,1)=0, bit3: XNOR(1,1)=1
        // bit4: XNOR(0,0)=1, bit5: XNOR(1,0)=0, bit6: XNOR(0,0)=1, bit7: XNOR(1,0)=0
        // → 01010101 → popcount = 4 → >= 4 → 1
        BitSet input3 = new BitSet(8);
        input3.set(1);
        input3.set(3);
        input3.set(5);
        input3.set(7);
        assertThat(neuron.forward(input3)).isEqualTo(1);
    }

    @Test
    void activationShouldReturnPopcount() {
        int[] hw = {1, 1, 1, 1, 0, 0, 0, 0};
        BinaryNeuron neuron = new BinaryNeuron(8, 4, hw);

        // All 1s input: XNOR with 11110000 = 11110000 → popcount = 4
        BitSet input = allSet(8);
        assertThat(neuron.computeActivation(input)).isEqualTo(4);

        // All 0s input: XNOR with 11110000 = 00001111 → popcount = 4
        BitSet input2 = new BitSet(8);
        assertThat(neuron.computeActivation(input2)).isEqualTo(4);
    }

    @Test
    void evaluateIntShouldMatchBitSetEvaluate() {
        BinaryNeuron neuron = new BinaryNeuron(8, 4, rng);

        for (int i = 0; i < 256; i++) {
            BitSet bs = intToBitSet(i, 8);
            assertThat(neuron.evaluate(i))
                    .as("input=%d", i)
                    .isEqualTo(neuron.forward(bs) == 1);
        }
    }

    // ─── Weight Updates ───

    @Test
    void trainStepShouldUpdateWeights() {
        int[] hw = {0, 0, 0, 0, 0, 0, 0, 0}; // all zeros
        BinaryNeuron neuron = new BinaryNeuron(8, 4, hw);

        BitSet input = allSet(8);
        int target = 1; // want activation

        int[] before = neuron.hiddenWeights().clone();
        neuron.trainStep(input, target);
        int[] after = neuron.hiddenWeights();

        // With all-zero weights and all-1 input, output=0, error=+1
        // Each weight should increment by +1 (direction = +1 for input=1)
        for (int i = 0; i < 8; i++) {
            assertThat(after[i]).isEqualTo(before[i] + 1);
        }
    }

    @Test
    void trainStepShouldDecrementWeightsOnError() {
        int[] hw = {1, 1, 1, 1, 1, 1, 1, 1}; // all ones
        BinaryNeuron neuron = new BinaryNeuron(8, 4, hw);

        BitSet input = allSet(8);
        int target = 0; // want deactivation

        int[] before = neuron.hiddenWeights().clone();
        neuron.trainStep(input, target);
        int[] after = neuron.hiddenWeights();

        // With all-1 weights and all-1 input, output=1, error=-1
        // Each weight should decrement by -1 (direction = +1 for input=1, error=-1)
        for (int i = 0; i < 8; i++) {
            assertThat(after[i]).isEqualTo(before[i] - 1);
        }
    }

    @Test
    void trainStepNoChangeWhenCorrect() {
        int[] hw = {1, 1, 1, 1, 0, 0, 0, 0};
        BinaryNeuron neuron = new BinaryNeuron(8, 4, hw);

        // Input matching weights → output=1, target=1 → error=0
        BitSet input = new BitSet(8);
        input.set(0, 4);
        int target = 1;

        int[] before = neuron.hiddenWeights().clone();
        int error = neuron.trainStep(input, target);
        int[] after = neuron.hiddenWeights();

        assertThat(error).isEqualTo(0);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void trainStepShouldReturnErrorSignal() {
        int[] hw = {0, 0, 0, 0, 0, 0, 0, 0};
        BinaryNeuron neuron = new BinaryNeuron(8, 4, hw);

        BitSet input = allSet(8);

        // target=1, output=0 → error=+1
        assertThat(neuron.trainStep(input, 1)).isEqualTo(1);

        // After one update, weights are {1,1,1,1,1,1,1,1}
        // target=0, output=1 → error=-1
        assertThat(neuron.trainStep(input, 0)).isEqualTo(-1);
    }

    @Test
    void binaryWeightsShouldReflectHiddenWeights() {
        int[] hw = {1, -1, 0, 2, -5, 3, -2, 0};
        BinaryNeuron neuron = new BinaryNeuron(8, 4, hw);

        BitSet bw = neuron.binaryWeights();
        assertThat(bw.get(0)).isTrue();   // 1 > 0
        assertThat(bw.get(1)).isFalse();  // -1 <= 0
        assertThat(bw.get(2)).isFalse();  // 0 <= 0
        assertThat(bw.get(3)).isTrue();   // 2 > 0
        assertThat(bw.get(4)).isFalse();  // -5 <= 0
        assertThat(bw.get(5)).isTrue();   // 3 > 0
        assertThat(bw.get(6)).isFalse();  // -2 <= 0
        assertThat(bw.get(7)).isFalse();  // 0 <= 0
    }

    // ─── TruthTable integration ───

    @Test
    void evaluateIntShouldWork() {
        int[] hw = {1, 1, 0, 0};
        BinaryNeuron neuron = new BinaryNeuron(4, 2, hw);

        // Input 0b1010 = 10: bits are [0,1,0,1]
        // Weights: [1,1,0,0]
        // XNOR: [1,0,1,0] → popcount=2 ≥ threshold=2 → true
        assertThat(neuron.evaluate(0b1010)).isTrue();

        // Input 0b0101 = 5: bits are [1,0,1,0]
        // XNOR: [0,1,0,1] → popcount=2 ≥ threshold=2 → true
        assertThat(neuron.evaluate(0b0101)).isTrue();

        // Input 0b0000 = 0: bits are [0,0,0,0]
        // XNOR: [0,0,1,1] → popcount=2 ≥ threshold=2 → true
        assertThat(neuron.evaluate(0b0000)).isTrue();
    }

    // ─── Weight popcount ───

    @Test
    void weightPopcountShouldCountSetBits() {
        int[] hw = {1, 0, 1, 0, 1, 0, 1, 0};
        BinaryNeuron neuron = new BinaryNeuron(8, 4, hw);
        assertThat(neuron.weightPopcount()).isEqualTo(4);
    }

    // ─── Determinism ───

    @Test
    void sameSeedSameNeuron() {
        BinaryNeuron n1 = new BinaryNeuron(8, new Random(123));
        BinaryNeuron n2 = new BinaryNeuron(8, new Random(123));
        assertThat(n1).isEqualTo(n2);
    }

    @Test
    void differentSeedDifferentNeuron() {
        BinaryNeuron n1 = new BinaryNeuron(8, new Random(1));
        BinaryNeuron n2 = new BinaryNeuron(8, new Random(2));
        // Different seeds → different weights (with overwhelming probability)
        assertThat(n1).isNotEqualTo(n2);
    }

    // ─── toString ───

    @Test
    void toStringIsDescriptive() {
        BinaryNeuron neuron = new BinaryNeuron(8, rng);
        String str = neuron.toString();
        assertThat(str).contains("BinaryNeuron").contains("inputSize=8");
    }

    // ─── Helpers ───

    private static BitSet allSet(int size) {
        BitSet bs = new BitSet(size);
        bs.set(0, size);
        return bs;
    }

    private static BitSet intToBitSet(int value, int size) {
        BitSet bs = new BitSet(size);
        for (int i = 0; i < size; i++) {
            if (((value >>> i) & 1) == 1) {
                bs.set(i);
            }
        }
        return bs;
    }
}
