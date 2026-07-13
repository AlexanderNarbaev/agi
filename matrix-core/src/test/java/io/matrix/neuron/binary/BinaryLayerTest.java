package io.matrix.neuron.binary;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinaryLayerTest {

    private final Random rng = new Random(42);

    // ─── Construction ───

    @Test
    void shouldCreateWithCorrectDimensions() {
        BinaryLayer layer = new BinaryLayer(8, 4, rng);
        assertThat(layer.inputSize()).isEqualTo(8);
        assertThat(layer.outputSize()).isEqualTo(4);
        assertThat(layer.neurons()).hasSize(4);
    }

    @Test
    void shouldRejectInvalidInputSize() {
        assertThatThrownBy(() -> new BinaryLayer(0, 4, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidOutputSize() {
        assertThatThrownBy(() -> new BinaryLayer(8, 0, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCreateFromPrebuiltNeurons() {
        BinaryNeuron n1 = new BinaryNeuron(8, rng);
        BinaryNeuron n2 = new BinaryNeuron(8, rng);
        BinaryLayer layer = new BinaryLayer(List.of(n1, n2));

        assertThat(layer.outputSize()).isEqualTo(2);
        assertThat(layer.inputSize()).isEqualTo(8);
    }

    @Test
    void shouldRejectEmptyNeuronList() {
        assertThatThrownBy(() -> new BinaryLayer(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMismatchedNeuronInputSizes() {
        BinaryNeuron n1 = new BinaryNeuron(8, rng);
        BinaryNeuron n2 = new BinaryNeuron(4, rng);
        assertThatThrownBy(() -> new BinaryLayer(List.of(n1, n2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Forward pass ───

    @Test
    void forwardShouldProduceCorrectOutputSize() {
        BinaryLayer layer = new BinaryLayer(8, 4, rng);
        BitSet input = randomBitSet(8, rng);

        BitSet output = layer.forward(input);
        assertThat(output.length()).isLessThanOrEqualTo(4);
    }

    @Test
    void forwardShouldBeDeterministic() {
        BinaryLayer layer = new BinaryLayer(8, 4, rng);
        BitSet input = randomBitSet(8, rng);

        BitSet out1 = layer.forward(input);
        BitSet out2 = layer.forward(input);
        assertThat(out1).isEqualTo(out2);
    }

    @Test
    void forwardAllZeroInput() {
        BinaryLayer layer = new BinaryLayer(8, 4, rng);
        BitSet input = new BitSet(8);

        BitSet output = layer.forward(input);
        assertThat(output.length()).isLessThanOrEqualTo(4);
    }

    @Test
    void forwardAllOneInput() {
        BinaryLayer layer = new BinaryLayer(8, 4, rng);
        BitSet input = allSet(8);

        BitSet output = layer.forward(input);
        assertThat(output.length()).isLessThanOrEqualTo(4);
    }

    @Test
    void outputBitsMatchNeuronActivations() {
        BinaryLayer layer = new BinaryLayer(8, 3, rng);
        BitSet input = randomBitSet(8, rng);

        BitSet output = layer.forward(input);

        // Verify each bit matches the corresponding neuron's forward result
        for (int i = 0; i < 3; i++) {
            int neuronOutput = layer.neurons().get(i).forward(input);
            assertThat(output.get(i))
                    .as("neuron %d", i)
                    .isEqualTo(neuronOutput == 1);
        }
    }

    // ─── Training ───

    @Test
    void trainStepShouldReturnErrorVector() {
        BinaryLayer layer = new BinaryLayer(8, 4, rng);
        BitSet input = randomBitSet(8, rng);
        BitSet target = randomBitSet(4, rng);

        int[] errors = layer.trainStep(input, target);
        assertThat(errors).hasSize(4);

        // Each error should be -1, 0, or +1
        for (int error : errors) {
            assertThat(error).isIn(-1, 0, 1);
        }
    }

    @Test
    void trainStepShouldUpdateWeights() {
        BinaryLayer layer = new BinaryLayer(8, 4, rng);
        BitSet input = randomBitSet(8, rng);
        BitSet target = allSet(4); // all targets = 1

        int[] beforeWeights = layer.neurons().get(0).hiddenWeights().clone();
        layer.trainStep(input, target);
        int[] afterWeights = layer.neurons().get(0).hiddenWeights();

        // At least one weight should have changed (unless already perfect)
        boolean changed = false;
        for (int i = 0; i < beforeWeights.length; i++) {
            if (beforeWeights[i] != afterWeights[i]) {
                changed = true;
                break;
            }
        }
        // If all neurons already output 1, no change needed
        // Otherwise, weights should change
        BitSet output = layer.forward(input);
        if (!output.equals(target)) {
            assertThat(changed).isTrue();
        }
    }

    @Test
    void computeErrorsShouldNotUpdateWeights() {
        BinaryLayer layer = new BinaryLayer(8, 4, rng);
        BitSet input = randomBitSet(8, rng);
        BitSet target = allSet(4);

        int[] beforeWeights = layer.neurons().get(0).hiddenWeights().clone();
        layer.computeErrors(input, target);
        int[] afterWeights = layer.neurons().get(0).hiddenWeights();

        assertThat(afterWeights).isEqualTo(beforeWeights);
    }

    @Test
    void errorToBitSetShouldReflectNonZeroErrors() {
        BinaryLayer layer = new BinaryLayer(8, 4, rng);
        int[] errors = {1, 0, -1, 0};

        BitSet errorBits = layer.errorToBitSet(errors);
        assertThat(errorBits.get(0)).isTrue();  // error=1
        assertThat(errorBits.get(1)).isFalse(); // error=0
        assertThat(errorBits.get(2)).isTrue();  // error=-1
        assertThat(errorBits.get(3)).isFalse(); // error=0
    }

    // ─── Memory ───

    @Test
    void totalWeightBitsShouldBeInputTimesOutput() {
        BinaryLayer layer = new BinaryLayer(8, 4, rng);
        assertThat(layer.totalWeightBits()).isEqualTo(32);
    }

    @Test
    void memoryBytesShouldBePositive() {
        BinaryLayer layer = new BinaryLayer(8, 4, rng);
        assertThat(layer.memoryBytes()).isPositive();
    }

    // ─── Single neuron layer ───

    @Test
    void singleNeuronLayer() {
        BinaryLayer layer = new BinaryLayer(8, 1, rng);
        BitSet input = randomBitSet(8, rng);

        BitSet output = layer.forward(input);
        assertThat(output.length()).isLessThanOrEqualTo(1);
    }

    // ─── Determinism ───

    @Test
    void sameSeedSameLayer() {
        BinaryLayer l1 = new BinaryLayer(8, 4, new Random(123));
        BinaryLayer l2 = new BinaryLayer(8, 4, new Random(123));
        assertThat(l1).isEqualTo(l2);
    }

    // ─── toString ───

    @Test
    void toStringIsDescriptive() {
        BinaryLayer layer = new BinaryLayer(8, 4, rng);
        String str = layer.toString();
        assertThat(str).contains("BinaryLayer").contains("8").contains("4");
    }

    // ─── Helpers ───

    private static BitSet randomBitSet(int size, Random rng) {
        BitSet bs = new BitSet(size);
        for (int i = 0; i < size; i++) {
            if (rng.nextBoolean()) {
                bs.set(i);
            }
        }
        return bs;
    }

    private static BitSet allSet(int size) {
        BitSet bs = new BitSet(size);
        bs.set(0, size);
        return bs;
    }
}
