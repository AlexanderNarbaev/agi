package io.matrix.research;

import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.Neurotransmitter;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RUN 339 — DESIGN-20 EnrichedNeuron foundation tests.
 *
 * <p>Acceptance criteria from DESIGN-20 §8:
 *  1. EnrichedNeuron record + factory ✅
 *  2. magnitude ∈ [0,1] for random tables ✅
 *  3. chemicalVector 4D valid ✅
 *  4. magnitude + chemical reproducible (deterministic) ✅
 *  5. tag classified ✅
 *  6. HammingDistance + chemicalDistance utilities ✅
 */
class Exp339EnrichedNeuronTest {

    @Test
    void enrichedNeuronHasValidStructure() {
        // Build a TruthTable with k=8 and 128 set bits (density 0.5)
        TruthTable table = TruthTable.of(8, makeBitSet(8, 128, 0xACE0L));
        boolean[] input = new boolean[]{true, false, true, true, false, true, false, true};

        EnrichedNeuron neuron = EnrichedNeuron.derive(table, input);

        assertThat(neuron.table()).isSameAs(table);
        assertThat(neuron.magnitude()).isBetween(0.0, 1.0);
        assertThat(neuron.chemicalVector()).hasSize(EnrichedNeuron.CHEMICAL_DIM);
        for (double v : neuron.chemicalVector()) {
            assertThat(v).isBetween(0.0, 1.0);
        }
        assertThat(neuron.tag()).isNotNull();
        // excitation + inhibition == 1 by construction
        double sum = neuron.chemicalVector()[EnrichedNeuron.EXCITATION]
                + neuron.chemicalVector()[EnrichedNeuron.INHIBITION];
        assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void magnitudeIsBoundedForAllDensities() {
        // 100 random tables with k=14, density 0..1
        Random rng = new Random(0xBEEFL);
        for (int trial = 0; trial < 100; trial++) {
            int k = 14;
            int card = rng.nextInt(1 << k);
            TruthTable table = TruthTable.of(k, makeBitSet(k, card, rng.nextLong()));
            double m = EnrichedNeuron.magnitudeFromTable(table);
            assertThat(m).as("magnitude in [0,1] for table %d", trial)
                    .isBetween(0.0, 1.0);
        }
    }

    @Test
    void deterministicReproducibility() {
        // Same inputs → same outputs (CONSTITUTION I)
        TruthTable table = TruthTable.of(10, makeBitSet(10, 512, 0xDEADBEEFL));
        boolean[] input = new boolean[]{true, false, true, true, false, true, false, true, false, true};

        EnrichedNeuron a = EnrichedNeuron.derive(table, input);
        EnrichedNeuron b = EnrichedNeuron.derive(table, input);

        assertThat(a.magnitude()).isEqualTo(b.magnitude());
        assertThat(a.chemicalVector()).isEqualTo(b.chemicalVector());
        assertThat(a.tag()).isEqualTo(b.tag());
    }

    @Test
    void chemicalVectorSumConstraints() {
        // excitation + inhibition = 1 (always)
        TruthTable table = TruthTable.of(8, makeBitSet(8, 200, 0x1234L));
        boolean[] input = new boolean[]{true, false, true, true, false, true, false, true};

        EnrichedNeuron neuron = EnrichedNeuron.derive(table, input);
        double sum = neuron.chemicalVector()[EnrichedNeuron.EXCITATION]
                + neuron.chemicalVector()[EnrichedNeuron.INHIBITION];
        assertThat(sum).as("excitation + inhibition == 1").isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void hammingDistanceSymmetric() {
        TruthTable t1 = TruthTable.of(8, makeBitSet(8, 100, 0xAAAAAAL));
        TruthTable t2 = TruthTable.of(8, makeBitSet(8, 150, 0x55555L));
        EnrichedNeuron a = EnrichedNeuron.derive(t1);
        EnrichedNeuron b = EnrichedNeuron.derive(t2);

        double d_ab = EnrichedNeuron.hammingDistance(a, b);
        double d_ba = EnrichedNeuron.hammingDistance(b, a);
        assertThat(d_ab).isEqualTo(d_ba);
        assertThat(d_ab).isBetween(0.0, 1.0);
    }

    @Test
    void hammingDistanceZeroForIdentical() {
        TruthTable t1 = TruthTable.of(8, makeBitSet(8, 100, 0xABCDL));
        TruthTable t2 = TruthTable.of(8, makeBitSet(8, 100, 0xABCDL));
        EnrichedNeuron a = EnrichedNeuron.derive(t1);
        EnrichedNeuron b = EnrichedNeuron.derive(t2);

        assertThat(EnrichedNeuron.hammingDistance(a, b)).isEqualTo(0.0);
    }

    @Test
    void tagClassificationDeterministic() {
        // Same inputs → same tag
        TruthTable table = TruthTable.of(8, makeBitSet(8, 200, 0x9999L));
        boolean[] input = new boolean[]{true, true, true, true, false, false, false, false};

        Neurotransmitter tag1 = EnrichedNeuron.classify(
                EnrichedNeuron.chemicalFromInput(table, input));
        Neurotransmitter tag2 = EnrichedNeuron.classify(
                EnrichedNeuron.chemicalFromInput(table, input));

        assertThat(tag1).isEqualTo(tag2);
        assertThat(tag1).isIn(
                Neurotransmitter.NOREPINEPHRINE, Neurotransmitter.DOPAMINE,
                Neurotransmitter.SEROTONIN, Neurotransmitter.GABA,
                Neurotransmitter.GLUTAMATE, Neurotransmitter.ACETYLCHOLINE);
    }

    @Test
    void constructorRejectsBadMagnitude() {
        TruthTable table = TruthTable.of(4, makeBitSet(4, 5, 0xAL));
        double[] chem = new double[]{0.5, 0.5, 0.5, 0.5};
        assertThatThrownBy(() -> new EnrichedNeuron(table, -0.1, chem, Neurotransmitter.SEROTONIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("magnitude");
        assertThatThrownBy(() -> new EnrichedNeuron(table, 1.5, chem, Neurotransmitter.SEROTONIN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsBadChemicalVector() {
        TruthTable table = TruthTable.of(4, makeBitSet(4, 5, 0xAL));
        double[] badChem = new double[]{0.5, 0.5, 0.5};  // only 3 dims
        assertThatThrownBy(() -> new EnrichedNeuron(table, 0.5, badChem, Neurotransmitter.SEROTONIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chemicalVector");
    }

    @Test
    void chemicalFromInputValidatesLength() {
        TruthTable table = TruthTable.of(8, makeBitSet(8, 100, 0xBL));
        boolean[] badInput = new boolean[5];  // wrong length
        assertThatThrownBy(() -> EnrichedNeuron.chemicalFromInput(table, badInput))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BitSet makeBitSet(int k, int cardinality, long seed) {
        BitSet bs = new BitSet(1 << k);
        Random rng = new Random(seed);
        for (int i = 0; i < cardinality; i++) {
            bs.set(rng.nextInt(1 << k));
        }
        return bs;
    }
}
