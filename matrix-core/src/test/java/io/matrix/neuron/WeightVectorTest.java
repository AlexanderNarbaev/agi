package io.matrix.neuron;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.cluster.Signal;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightVectorTest {

    @Test
    void shouldCreateUniformVector() {
        WeightVector w = WeightVector.uniform(5);
        assertThat(w.size()).isEqualTo(5);
        for (int i = 0; i < 5; i++) {
            assertThat(w.weight(i)).isEqualTo(2);
        }
    }

    @Test
    void uniformShouldRejectZeroOrNegativeK() {
        assertThatThrownBy(() -> WeightVector.uniform(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WeightVector.uniform(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCreateRandomVector() {
        Random rng = new Random(42);
        WeightVector w = WeightVector.random(10, rng);
        assertThat(w.size()).isEqualTo(10);
        for (int i = 0; i < 10; i++) {
            assertThat(w.weight(i)).isBetween(1, 3);
        }
    }

    @Test
    void randomShouldBeReproducibleWithSeed() {
        WeightVector a = WeightVector.random(8, new Random(123));
        WeightVector b = WeightVector.random(8, new Random(123));
        assertThat(a.toArray()).isEqualTo(b.toArray());
    }

    @Test
    void priorityOrderShouldSortByWeightDescending() {
        // weights: [1, 3, 2, 3, 1]
        // priority order: index 1 (w=3), index 3 (w=3), index 2 (w=2), index 0 (w=1), index 4 (w=1)
        WeightVector w = new WeightVector(new int[]{1, 3, 2, 3, 1});
        int[] order = w.priorityOrder();
        assertThat(order).containsExactly(1, 3, 2, 0, 4);
    }

    @Test
    void priorityOrderShouldBeStableForEqualWeights() {
        WeightVector w = WeightVector.uniform(4);
        int[] order = w.priorityOrder();
        // All weights are 2, so order should be 0,1,2,3 (stable, index-sorted)
        assertThat(order).containsExactly(0, 1, 2, 3);
    }

    @Test
    void priorityOrderForSingleElement() {
        WeightVector w = new WeightVector(new int[]{3});
        assertThat(w.priorityOrder()).containsExactly(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 4, 5, 100})
    void shouldRejectInvalidWeights(int invalidWeight) {
        assertThatThrownBy(() -> new WeightVector(new int[]{invalidWeight}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Weight must be 1-3");
    }

    @Test
    void shouldRejectNullWeights() {
        assertThatThrownBy(() -> new WeightVector(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void weightShouldThrowForOutOfRangeIndex() {
        WeightVector w = WeightVector.uniform(3);
        assertThatThrownBy(() -> w.weight(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> w.weight(3)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void toArrayShouldReturnDefensiveCopy() {
        WeightVector w = new WeightVector(new int[]{1, 2, 3});
        int[] arr = w.toArray();
        arr[0] = 99;
        assertThat(w.weight(0)).isEqualTo(1);
    }

    @Test
    void constructorShouldDefensivelyCopy() {
        int[] original = {1, 2, 3};
        WeightVector w = new WeightVector(original);
        original[0] = 99;
        assertThat(w.weight(0)).isEqualTo(1);
    }

    @Nested
    class NeuronInstanceIntegration {

        @Test
        void neuronInstanceShouldExposeWeightsFromTruthTable() {
            WeightVector weights = new WeightVector(new int[]{3, 1, 2});
            TruthTable table = TruthTable.random(3, new Random(7), weights);
            NeuronInstance neuron = NeuronInstance.stable(NeuronId.create(), table);

            assertThat(neuron.weights()).isNotNull();
            assertThat(neuron.weights().toArray()).containsExactly(3, 1, 2);
        }

        @Test
        void neuronInstanceShouldAcceptExplicitWeights() {
            TruthTable table = TruthTable.random(3);
            WeightVector weights = WeightVector.uniform(3);
            NeuronInstance neuron = NeuronInstance.stable(
                    NeuronId.create(), table, weights);

            assertThat(neuron.weights()).isEqualTo(weights);
        }

        @Test
        void weightedTruthTableEvaluationShouldPermuteInputs() {
            // k=2, weights=[3,1] → priority order = [0, 1] (index 0 has higher weight)
            // Since priority order is [0, 1], permutation is identity.
            // Let's use weights=[1,3] → priority order = [1, 0]
            // This means: when evaluating input {bit0=a, bit1=b},
            // the table is indexed with permuted index {bit0=b, bit1=a}
            BitSet table = new BitSet(4);
            table.set(0b01); // output is 1 when permuted index = 1 (bit0=1, bit1=0)
            WeightVector weights = new WeightVector(new int[]{1, 3});
            TruthTable tt = TruthTable.of(2, table, weights);

            // input bit0=0, bit1=1 → permuted: bit0=1, bit1=0 → index=1 → output=true
            BitSet input1 = new BitSet(2);
            input1.set(1);
            assertThat(tt.evaluate(input1)).isTrue();

            // input bit0=1, bit1=0 → permuted: bit0=0, bit1=1 → index=2 → output=false
            BitSet input2 = new BitSet(2);
            input2.set(0);
            assertThat(tt.evaluate(input2)).isFalse();

            // input bit0=0, bit1=0 → permuted: 0 → output=false
            assertThat(tt.evaluate(new BitSet(2))).isFalse();
        }

        @Test
        void weightedAndUnweightedEvaluationShouldDiffer() {
            // Same truth table content, but one has weights that permute inputs
            BitSet table = new BitSet(4);
            table.set(0b01); // output=1 only when index=1
            TruthTable plain = TruthTable.of(2, table);
            WeightVector weights = new WeightVector(new int[]{1, 3}); // priority=[1,0]
            TruthTable weighted = TruthTable.of(2, table, weights);

            BitSet input = new BitSet(2);
            input.set(1); // bit0=0, bit1=1

            // Plain: index = 0b10 = 2 → output=false
            assertThat(plain.evaluate(input)).isFalse();
            // Weighted: permuted index = 0b01 = 1 → output=true
            assertThat(weighted.evaluate(input)).isTrue();
        }

        @Test
        void avroSerializationShouldRoundtripWithWeights() {
            WeightVector weights = new WeightVector(new int[]{3, 1, 2, 3});
            TruthTable original = TruthTable.random(4, new Random(99), weights);
            byte[] bytes = original.toAvroBytes();
            TruthTable restored = TruthTable.fromAvroBytes(bytes);

            assertThat(restored.k()).isEqualTo(original.k());
            assertThat(restored.table()).isEqualTo(original.table());
            assertThat(restored.weights()).isNotNull();
            assertThat(restored.weights().toArray()).containsExactly(3, 1, 2, 3);
            assertThat(restored).isEqualTo(original);
        }

        @Test
        void avroSerializationShouldRoundtripWithoutWeights() {
            TruthTable original = TruthTable.random(4, new Random(99));
            byte[] bytes = original.toAvroBytes();
            TruthTable restored = TruthTable.fromAvroBytes(bytes);

            assertThat(restored.weights()).isNull();
            assertThat(restored).isEqualTo(original);
        }
    }
}
