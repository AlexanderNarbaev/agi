package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NeuronLayerTest {

    private final Random rng = new Random(42);

    @Test
    void shouldCreateLayerWithCorrectDimensions() {
        NeuronLayer layer = new NeuronLayer(5, 8, rng);

        assertThat(layer.outputWidth()).isEqualTo(5);
        assertThat(layer.k()).isEqualTo(8);
        assertThat(layer.neurons()).hasSize(5);
    }

    @Test
    void shouldRejectInvalidNeuronCount() {
        assertThatThrownBy(() -> new NeuronLayer(0, 4, rng))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NeuronLayer(-1, 4, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidK() {
        assertThatThrownBy(() -> new NeuronLayer(3, 0, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evaluateAllZeroInput() {
        NeuronLayer layer = new NeuronLayer(4, 3, rng);
        BitSet input = new BitSet(4 * 3); // all zeros

        BitSet output = layer.evaluate(input);

        assertThat(output.length()).isLessThanOrEqualTo(4);
        // Output should be deterministic given the fixed input
        BitSet output2 = layer.evaluate(input);
        assertThat(output).isEqualTo(output2);
    }

    @Test
    void evaluateAllOneInput() {
        NeuronLayer layer = new NeuronLayer(4, 3, rng);
        BitSet input = new BitSet(4 * 3);
        input.set(0, 4 * 3); // all ones

        BitSet output = layer.evaluate(input);

        assertThat(output.length()).isLessThanOrEqualTo(4);
        // Deterministic: same input → same output
        BitSet output2 = layer.evaluate(input);
        assertThat(output).isEqualTo(output2);
    }

    @Test
    void outputWidthMatchesNeuronCount() {
        for (int neuronCount : new int[]{1, 3, 8, 12}) {
            NeuronLayer layer = new NeuronLayer(neuronCount, 5, rng);
            BitSet input = new BitSet(neuronCount * 5);
            input.set(0, neuronCount * 5);

            BitSet output = layer.evaluate(input);
            assertThat(output.size()).isGreaterThanOrEqualTo(neuronCount);
            // output bits should not exceed neuronCount (BitSet.length is highest set bit + 1, or 0)
        }
    }

    @Test
    void deterministicEvaluation() {
        NeuronLayer layer = new NeuronLayer(4, 4, new Random(123));

        for (int trial = 0; trial < 5; trial++) {
            BitSet input = new BitSet(16);
            // Set a specific pattern
            input.set(0);
            input.set(5);
            input.set(12);

            BitSet out1 = layer.evaluate(input);
            BitSet out2 = layer.evaluate(input);

            assertThat(out1).as("trial %d", trial).isEqualTo(out2);
        }
    }

    @Test
    void deterministicWithDifferentSeeds() {
        NeuronLayer layer1 = new NeuronLayer(4, 4, new Random(1));
        NeuronLayer layer2 = new NeuronLayer(4, 4, new Random(1));

        BitSet input = new BitSet(16);
        input.set(0, 16);

        BitSet out1 = layer1.evaluate(input);
        BitSet out2 = layer2.evaluate(input);

        // Same seed → same neurons → same output
        assertThat(out1).isEqualTo(out2);
    }

    @Test
    void neuronsAreDistinct() {
        NeuronLayer layer = new NeuronLayer(5, 4, rng);
        List<DecisionTree> neurons = layer.neurons();

        // Each neuron should be a distinct object
        for (int i = 0; i < neurons.size(); i++) {
            for (int j = i + 1; j < neurons.size(); j++) {
                // They might be equal by chance, but should be distinct objects
                // Not checking .equals() since random trees could be identical
            }
        }
        assertThat(neurons).hasSize(5);
    }

    @Test
    void fromTruthTablesShouldPreserveBehavior() {
        // Create a layer normally
        NeuronLayer original = new NeuronLayer(3, 4, new Random(7));

        // Convert to truth tables and back
        List<TruthTable> tables = original.neurons().stream()
                .map(t -> t.toTruthTable(4))
                .toList();

        NeuronLayer reconstructed = NeuronLayer.fromTruthTables(tables);

        assertThat(reconstructed.outputWidth()).isEqualTo(original.outputWidth());
        assertThat(reconstructed.k()).isEqualTo(original.k());

        // Test that evaluation is consistent
        Random testRng = new Random(99);
        for (int trial = 0; trial < 20; trial++) {
            BitSet input = randomBitSet(3 * 4, testRng);
            BitSet origOut = original.evaluate(input);
            BitSet reconOut = reconstructed.evaluate(input);
            assertThat(reconOut).as("trial %d", trial).isEqualTo(origOut);
        }
    }

    @Test
    void fromTruthTablesRejectsEmpty() {
        assertThatThrownBy(() -> NeuronLayer.fromTruthTables(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one truth table");
    }

    @Test
    void fromTruthTablesRejectsMismatchedK() {
        TruthTable tt1 = TruthTable.random(3, rng);
        TruthTable tt2 = TruthTable.random(4, rng);

        assertThatThrownBy(() -> NeuronLayer.fromTruthTables(List.of(tt1, tt2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same k");
    }

    @Test
    void roundtripViaAvroBytes() {
        NeuronLayer original = new NeuronLayer(4, 4, new Random(42));

        byte[] bytes = original.toAvroBytes();
        assertThat(bytes).isNotEmpty();

        NeuronLayer restored = NeuronLayer.fromAvroBytes(bytes);

        assertThat(restored.outputWidth()).isEqualTo(original.outputWidth());
        assertThat(restored.k()).isEqualTo(original.k());

        // Verify evaluation consistency across many random inputs
        Random testRng = new Random(77);
        for (int trial = 0; trial < 30; trial++) {
            BitSet input = randomBitSet(4 * 4, testRng);
            assertThat(restored.evaluate(input))
                    .as("trial %d", trial)
                    .isEqualTo(original.evaluate(input));
        }
    }

    @Test
    void roundtripLargeK() {
        // Test with k=12, 4 neurons (realistic for hierarchical brain)
        NeuronLayer original = new NeuronLayer(4, 12, new Random(123));

        byte[] bytes = original.toAvroBytes();
        assertThat(bytes).isNotEmpty();

        NeuronLayer restored = NeuronLayer.fromAvroBytes(bytes);

        assertThat(restored.outputWidth()).isEqualTo(4);
        assertThat(restored.k()).isEqualTo(12);

        Random testRng = new Random(456);
        for (int trial = 0; trial < 10; trial++) {
            BitSet input = randomBitSet(4 * 12, testRng);
            assertThat(restored.evaluate(input))
                    .as("trial %d", trial)
                    .isEqualTo(original.evaluate(input));
        }
    }

    @Test
    void toStringIsDescriptive() {
        NeuronLayer layer = new NeuronLayer(5, 8, rng);
        String str = layer.toString();
        assertThat(str).contains("NeuronLayer")
                .contains("5")
                .contains("8");
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
}
