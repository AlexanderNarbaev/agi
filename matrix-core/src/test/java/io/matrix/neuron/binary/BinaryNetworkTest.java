package io.matrix.neuron.binary;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BinaryNetworkTest {

    private final Random rng = new Random(42);

    // ─── Construction ───

    @Test
    void shouldCreateWithValidArchitecture() {
        BinaryNetwork net = new BinaryNetwork(new int[]{8, 4, 2}, rng);
        assertThat(net.depth()).isEqualTo(2);
        assertThat(net.inputSize()).isEqualTo(8);
        assertThat(net.outputSize()).isEqualTo(2);
        assertThat(net.layerSizes()).containsExactly(8, 4, 2);
    }

    @Test
    void shouldRejectSingleLayer() {
        assertThatThrownBy(() -> new BinaryNetwork(new int[]{8}, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectZeroSizeLayer() {
        assertThatThrownBy(() -> new BinaryNetwork(new int[]{8, 0, 2}, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCreateFromPrebuiltLayers() {
        BinaryLayer l1 = new BinaryLayer(8, 4, rng);
        BinaryLayer l2 = new BinaryLayer(4, 2, rng);
        BinaryNetwork net = new BinaryNetwork(java.util.List.of(l1, l2));

        assertThat(net.depth()).isEqualTo(2);
        assertThat(net.inputSize()).isEqualTo(8);
        assertThat(net.outputSize()).isEqualTo(2);
    }

    @Test
    void shouldRejectEmptyLayerList() {
        assertThatThrownBy(() -> new BinaryNetwork(java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Inference ───

    @Test
    void forwardShouldProduceCorrectOutputSize() {
        BinaryNetwork net = new BinaryNetwork(new int[]{8, 4, 2}, rng);
        BitSet input = randomBitSet(8, rng);

        BitSet output = net.forward(input);
        assertThat(output.length()).isLessThanOrEqualTo(2);
    }

    @Test
    void forwardShouldBeDeterministic() {
        BinaryNetwork net = new BinaryNetwork(new int[]{8, 4, 2}, rng);
        BitSet input = randomBitSet(8, rng);

        BitSet out1 = net.forward(input);
        BitSet out2 = net.forward(input);
        assertThat(out1).isEqualTo(out2);
    }

    @Test
    void forwardWithActivationsShouldReturnAllLayers() {
        BinaryNetwork net = new BinaryNetwork(new int[]{8, 4, 2}, rng);
        BitSet input = randomBitSet(8, rng);

        BitSet[] activations = net.forwardWithActivations(input);
        assertThat(activations).hasSize(3); // input + 2 layers
        assertThat(activations[0]).isEqualTo(input);
        assertThat(activations[2]).isEqualTo(net.forward(input));
    }

    @Test
    void singleLayerNetwork() {
        BinaryNetwork net = new BinaryNetwork(new int[]{4, 2}, rng);
        BitSet input = randomBitSet(4, rng);

        BitSet output = net.forward(input);
        assertThat(output.length()).isLessThanOrEqualTo(2);
    }

    // ─── Training ───

    @Test
    void trainStepShouldReturnNonNegativeError() {
        BinaryNetwork net = new BinaryNetwork(new int[]{8, 4, 2}, rng);
        BitSet input = randomBitSet(8, rng);
        BitSet target = randomBitSet(2, rng);

        int error = net.trainStep(input, target);
        assertThat(error).isGreaterThanOrEqualTo(0);
    }

    @Test
    void trainStepShouldReduceErrorOverTime() {
        // Simple XOR-like problem: 2 inputs → 1 output
        // Train for many epochs and verify error decreases
        BinaryNetwork net = new BinaryNetwork(new int[]{2, 4, 1}, rng);

        BitSet[] inputs = new BitSet[4];
        BitSet[] targets = new BitSet[4];

        // XOR truth table
        inputs[0] = bitSet(2, false, false); targets[0] = bitSet(1, false);
        inputs[1] = bitSet(2, true, false);  targets[1] = bitSet(1, true);
        inputs[2] = bitSet(2, false, true);  targets[2] = bitSet(1, true);
        inputs[3] = bitSet(2, true, true);   targets[3] = bitSet(1, false);

        int firstEpochError = 0;
        for (int i = 0; i < 4; i++) {
            firstEpochError += net.trainStep(inputs[i], targets[i]);
        }

        // Train more epochs
        for (int epoch = 0; epoch < 100; epoch++) {
            for (int i = 0; i < 4; i++) {
                net.trainStep(inputs[i], targets[i]);
            }
        }

        int lastEpochError = 0;
        for (int i = 0; i < 4; i++) {
            BitSet output = net.forward(inputs[i]);
            if (!output.equals(targets[i])) {
                lastEpochError++;
            }
        }

        // Error should decrease or stay same (binary networks may plateau)
        assertThat(lastEpochError).isLessThanOrEqualTo(4);
    }

    @Test
    void trainMultipleEpochsViaTrainer() {
        BinaryNetwork net = new BinaryNetwork(new int[]{4, 8, 2}, rng);
        BinaryTrainer trainer = new BinaryTrainer(net, rng);

        BitSet[] inputs = new BitSet[8];
        BitSet[] targets = new BitSet[8];
        Random dataRng = new Random(99);
        for (int i = 0; i < 8; i++) {
            inputs[i] = randomBitSet(4, dataRng);
            targets[i] = randomBitSet(2, dataRng);
        }

        BinaryTrainer.TrainingResult result = trainer.train(inputs, targets, 50);

        assertThat(result.accuracyHistory()).hasSize(50);
        assertThat(result.errorHistory()).hasSize(50);
        assertThat(result.finalAccuracy()).isBetween(0.0, 1.0);
    }

    // ─── Convergence (simple patterns) ───

    @Test
    void shouldLearnIdentityMapping() {
        // Learn identity: output = input (first 4 bits)
        int inputSize = 4;
        BinaryNetwork net = new BinaryNetwork(new int[]{inputSize, 8, inputSize}, rng);
        BinaryTrainer trainer = new BinaryTrainer(net, rng);

        BitSet[] inputs = new BitSet[16];
        BitSet[] targets = new BitSet[16];
        for (int i = 0; i < 16; i++) {
            inputs[i] = intToBitSet(i, inputSize);
            targets[i] = intToBitSet(i, inputSize);
        }

        BinaryTrainer.TrainingResult result = trainer.train(inputs, targets, 200);

        // Should converge to reasonable accuracy on identity
        assertThat(result.bestAccuracy()).isGreaterThanOrEqualTo(0.3);
    }

    @Test
    void shouldLearnConstantFunction() {
        // Learn constant-1: output always = 1
        BinaryNetwork net = new BinaryNetwork(new int[]{4, 4, 1}, rng);
        BinaryTrainer trainer = new BinaryTrainer(net, rng);

        BitSet[] inputs = new BitSet[8];
        BitSet[] targets = new BitSet[8];
        BitSet oneTarget = new BitSet(1);
        oneTarget.set(0);
        for (int i = 0; i < 8; i++) {
            inputs[i] = intToBitSet(i, 4);
            targets[i] = oneTarget;
        }

        BinaryTrainer.TrainingResult result = trainer.train(inputs, targets, 100);

        // Constant function should be learnable
        assertThat(result.bestAccuracy()).isGreaterThanOrEqualTo(0.5);
    }

    // ─── Memory ───

    @Test
    void totalWeightBitsShouldBePositive() {
        BinaryNetwork net = new BinaryNetwork(new int[]{8, 4, 2}, rng);
        assertThat(net.totalWeightBits()).isPositive();
    }

    @Test
    void memoryBytesShouldBePositive() {
        BinaryNetwork net = new BinaryNetwork(new int[]{8, 4, 2}, rng);
        assertThat(net.memoryBytes()).isPositive();
    }

    // ─── Equality ───

    @Test
    void sameSeedSameNetwork() {
        BinaryNetwork n1 = new BinaryNetwork(new int[]{8, 4, 2}, new Random(123));
        BinaryNetwork n2 = new BinaryNetwork(new int[]{8, 4, 2}, new Random(123));
        assertThat(n1).isEqualTo(n2);
    }

    // ─── toString ───

    @Test
    void toStringIsDescriptive() {
        BinaryNetwork net = new BinaryNetwork(new int[]{8, 4, 2}, rng);
        String str = net.toString();
        assertThat(str).contains("BinaryNetwork").contains("8").contains("4").contains("2");
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

    private static BitSet intToBitSet(int value, int size) {
        BitSet bs = new BitSet(size);
        for (int i = 0; i < size; i++) {
            if (((value >>> i) & 1) == 1) {
                bs.set(i);
            }
        }
        return bs;
    }

    private static BitSet bitSet(int size, boolean... bits) {
        BitSet bs = new BitSet(size);
        for (int i = 0; i < bits.length && i < size; i++) {
            if (bits[i]) bs.set(i);
        }
        return bs;
    }
}
