package io.matrix.research;

import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.Neurotransmitter;
import io.matrix.neuron.NeuronMerger;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 343 — DESIGN-22 §3 NeuronMerger.
 *
 * <p>Acceptance:
 *  - tryMerge returns empty when distances exceed thresholds
 *  - tryMerge returns merged neuron when all below
 *  - mergeAll bulk-merges duplicates
 *  - Pure function: deterministic
 */
class Exp343NeuronMergerTest {

    @Test
    void identicalNeuronsMerge() {
        TruthTable t = makeTable(8, 100, 0xAL);
        EnrichedNeuron a = EnrichedNeuron.derive(t);
        EnrichedNeuron b = EnrichedNeuron.derive(t);

        Optional<EnrichedNeuron> merged = NeuronMerger.tryMerge(a, b);
        assertThat(merged).isPresent();
        assertThat(merged.get().magnitude()).isCloseTo(a.magnitude(), within(1e-9));
        // When both are derived from the same TruthTable reference,
        // merger returns the base table directly (no XOR).
        assertThat(merged.get().table()).isSameAs(t);
    }

    @Test
    void veryDifferentNeuronsDoNotMerge() {
        TruthTable tA = makeTable(8, 10, 0xAL);    // density 10/256
        TruthTable tB = makeTable(8, 245, 0xBL);   // density 245/256
        EnrichedNeuron a = EnrichedNeuron.derive(tA);
        EnrichedNeuron b = EnrichedNeuron.derive(tB);

        Optional<EnrichedNeuron> merged = NeuronMerger.tryMerge(a, b);
        assertThat(merged).as("very different neurons should not merge").isEmpty();
    }

    @Test
    void kMismatchReturnsEmpty() {
        TruthTable tA = makeTable(8, 100, 0xAL);
        TruthTable tB = makeTable(10, 100, 0xBL);
        EnrichedNeuron a = EnrichedNeuron.derive(tA);
        EnrichedNeuron b = EnrichedNeuron.derive(tB);

        Optional<EnrichedNeuron> merged = NeuronMerger.tryMerge(a, b);
        assertThat(merged).as("k mismatch → no merge").isEmpty();
    }

    @Test
    void mergeAllBulkMergeDuplicates() {
        // Create 10 copies of the same table + 5 random others
        TruthTable base = makeTable(8, 128, 0xAL);
        List<EnrichedNeuron> input = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            input.add(EnrichedNeuron.derive(base));
        }
        for (int i = 0; i < 5; i++) {
            input.add(EnrichedNeuron.derive(makeTable(8, i * 30 + 10, i * 0xCCCL)));
        }

        List<EnrichedNeuron> merged = NeuronMerger.mergeAll(input);
        // 10 duplicates should collapse; 5 random stay
        assertThat(merged.size()).isLessThanOrEqualTo(6);  // <= 5 randoms + 1 merged
        assertThat(merged.size()).isGreaterThanOrEqualTo(5); // at least 5 randoms
        System.out.printf("[Exp343] input=%d, merged=%d (reduction %d)%n",
                input.size(), merged.size(), input.size() - merged.size());
    }

    @Test
    void mergedNeuronHasAveragedProperties() {
        // Use near-identical tables so default thresholds allow merge
        TruthTable tA = makeTable(8, 128, 0xAL);
        TruthTable tB = makeTable(8, 128, 0xBL);
        // Force closer cardinalities: tB copies tA, modifies few bits
        BitSet bSet = (BitSet) tA.table().clone();
        // Flip 5 random bits to keep hamming low
        bSet.flip(0); bSet.flip(10); bSet.flip(20); bSet.flip(30); bSet.flip(40);
        TruthTable tB2 = TruthTable.of(8, bSet);
        EnrichedNeuron a = EnrichedNeuron.derive(tA);
        EnrichedNeuron b = EnrichedNeuron.derive(tB2);

        Optional<EnrichedNeuron> merged = NeuronMerger.tryMerge(a, b);
        assertThat(merged).isPresent();
        // Magnitude is averaged
        double expectedMag = (a.magnitude() + b.magnitude()) / 2.0;
        assertThat(merged.get().magnitude()).isCloseTo(expectedMag, within(1e-9));
        // Chemical vector is averaged per dim
        for (int i = 0; i < EnrichedNeuron.CHEMICAL_DIM; i++) {
            double expectedChem = (a.chemicalVector()[i] + b.chemicalVector()[i]) / 2.0;
            assertThat(merged.get().chemicalVector()[i])
                    .isCloseTo(expectedChem, within(1e-9));
        }
    }

    @Test
    void mergeAllDeterministic() {
        TruthTable base = makeTable(8, 128, 0xAL);
        List<EnrichedNeuron> input = new ArrayList<>();
        for (int i = 0; i < 5; i++) input.add(EnrichedNeuron.derive(base));

        List<EnrichedNeuron> a = NeuronMerger.mergeAll(input);
        List<EnrichedNeuron> b = NeuronMerger.mergeAll(input);
        assertThat(a).hasSize(b.size());
        // (Magnitudes should be identical — pure function)
        for (int i = 0; i < a.size(); i++) {
            assertThat(a.get(i).magnitude()).isEqualTo(b.get(i).magnitude());
        }
    }

    @Test
    void mergeAllEmptyAndSingleton() {
        assertThat(NeuronMerger.mergeAll(List.of())).isEmpty();
        TruthTable t = makeTable(8, 100, 0xAL);
        List<EnrichedNeuron> single = List.of(EnrichedNeuron.derive(t));
        List<EnrichedNeuron> merged = NeuronMerger.mergeAll(single);
        assertThat(merged).hasSize(1);
    }

    @Test
    void customThresholdsWork() {
        TruthTable tA = makeTable(8, 100, 0xAL);
        TruthTable tB = makeTable(8, 130, 0xBL);
        EnrichedNeuron a = EnrichedNeuron.derive(tA);
        EnrichedNeuron b = EnrichedNeuron.derive(tB);

        // Default: probably won't merge (different density)
        Optional<EnrichedNeuron> defaultResult = NeuronMerger.tryMerge(a, b);

        // Loose thresholds: should merge
        Optional<EnrichedNeuron> looseResult = NeuronMerger.tryMerge(
                a, b, 1.0, 1.0, 1.0);
        assertThat(looseResult).isPresent();
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }

    private static TruthTable makeTable(int k, int cardinality, long seed) {
        BitSet bs = new BitSet(1 << k);
        Random rng = new Random(seed);
        for (int i = 0; i < cardinality; i++) bs.set(rng.nextInt(1 << k));
        return TruthTable.of(k, bs);
    }
}
