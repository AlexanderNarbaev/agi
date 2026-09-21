package io.matrix.research;

import io.matrix.cauldron.CauldronProtocolV2;
import io.matrix.lifecycle.FnlGateV2;
import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.Neurotransmitter;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 357 — Phase S Cauldron v2 (GMDH cycle + Φ-validation).
 */
class Exp357CauldronV2Test {

    @Test
    void cauldronGeneratesRow1Candidates() {
        CauldronProtocolV2 cauldron = new CauldronProtocolV2(20);
        cauldron.generateRow1(randomNeurons(10, 0xCAFE));
        assertThat(cauldron.candidates()).hasSize(10);
        assertThat(cauldron.candidates().get(0).row()).isEqualTo(1);
        assertThat(cauldron.candidates().get(0).parentA()).isEqualTo(-1);
    }

    @Test
    void cauldronGeneratesRow2Pairs() {
        CauldronProtocolV2 cauldron = new CauldronProtocolV2(20);
        // Use copies of same neuron so NeuronMerger.tryMerge succeeds
        // (random pairs fail strict Hamming threshold)
        List<EnrichedNeuron> sameNeuron = new ArrayList<>();
        for (int i = 0; i < 3; i++) sameNeuron.add(randomNeurons(1, 0xBEEFL).get(0));
        cauldron.generateRow1(sameNeuron);
        cauldron.generateRow2Pairs();
        // Row 2 candidates have parentA/parentB set
        long row2Count = cauldron.candidates().stream()
                .filter(c -> c.row() == 2).count();
        assertThat(row2Count).isGreaterThan(0);
        CauldronProtocolV2.CauldronCandidate firstPair =
                cauldron.candidates().stream()
                        .filter(c -> c.row() == 2).findFirst().orElseThrow();
        assertThat(firstPair.parentA()).isGreaterThanOrEqualTo(0);
        assertThat(firstPair.parentB()).isGreaterThan(firstPair.parentA());
    }

    @Test
    void cauldronPhiValidation() {
        CauldronProtocolV2 cauldron = new CauldronProtocolV2(20);
        cauldron.generateRow1(randomNeurons(5, 0xAL));
        // Make actuals + predictions
        List<Boolean> actual = new ArrayList<>();
        List<Boolean> predicted = new ArrayList<>();
        Random rng = new Random(0xF1L);
        for (int i = 0; i < 100; i++) {
            actual.add(rng.nextBoolean());
            predicted.add(rng.nextDouble() > 0.2);  // 80% match
        }
        CauldronProtocolV2.PhiResult result = cauldron.validate(0, actual, predicted);
        assertThat(result.candidateIndex()).isEqualTo(0);
        assertThat(result.accuracy()).isBetween(0.0, 1.0);
        assertThat(result.heldOutAccuracy()).isBetween(0.0, 1.0);
        // phiWin = heldOut - train
        assertThat(result.phiWin())
                .isCloseTo(result.heldOutAccuracy() - result.accuracy(),
                        org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void cauldronAdmitsBestToFnlGate() {
        CauldronProtocolV2 cauldron = new CauldronProtocolV2(20);
        cauldron.generateRow1(randomNeurons(3, 0xCAFE));
        // Add 2 phi results manually
        List<Boolean> actual = new ArrayList<>();
        List<Boolean> predicted = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            actual.add(i % 2 == 0);
            predicted.add(i % 2 == 0);  // 100% accurate
        }
        cauldron.validate(0, actual, predicted);
        cauldron.validate(1, actual, predicted);
        FnlGateV2 gate = new FnlGateV2(3);
        FnlGateV2.FnlEntry entry = cauldron.admitBest(gate);
        assertThat(entry).isNotNull();
        assertThat(entry.origin()).isEqualTo(FnlGateV2.FnlEntry.Origin.CAULDRON);
        assertThat(gate.size()).isEqualTo(1);
        assertThat(cauldron.stage()).isEqualTo(CauldronProtocolV2.Stage.COMPLETED);
    }

    @Test
    void cauldronStateMachineProgresses() {
        CauldronProtocolV2 cauldron = new CauldronProtocolV2(20);
        assertThat(cauldron.stage()).isEqualTo(CauldronProtocolV2.Stage.IDLE);
        cauldron.generateRow1(randomNeurons(3, 0xAL));
        assertThat(cauldron.stage()).isEqualTo(CauldronProtocolV2.Stage.GENERATING);
        cauldron.validate(0, List.of(true, false), List.of(true, true));
        assertThat(cauldron.stage()).isEqualTo(CauldronProtocolV2.Stage.VALIDATING);
        FnlGateV2 gate = new FnlGateV2(3);
        cauldron.admitBest(gate);
        assertThat(cauldron.stage()).isEqualTo(CauldronProtocolV2.Stage.COMPLETED);
    }

    @Test
    void cauldronEmptyRow1DoesNothingOnRow2() {
        CauldronProtocolV2 cauldron = new CauldronProtocolV2(20);
        cauldron.generateRow2Pairs();  // no row 1
        long row2Count = cauldron.candidates().stream()
                .filter(c -> c.row() == 2).count();
        assertThat(row2Count).isZero();
    }

    private static List<EnrichedNeuron> randomNeurons(int count, long seed) {
        List<EnrichedNeuron> list = new ArrayList<>();
        Random rng = new Random(seed);
        for (int i = 0; i < count; i++) {
            int k = 8;
            int card = rng.nextInt(1 << k);
            list.add(new EnrichedNeuron(makeTable(k, card, seed + i),
                    0.5, new double[]{0.5, 0.5, 0.5, 0.5},
                    Neurotransmitter.SEROTONIN));
        }
        return list;
    }

    private static TruthTable makeTable(int k, int cardinality, long seed) {
        BitSet bs = new BitSet(1 << k);
        Random rng = new Random(seed);
        for (int i = 0; i < cardinality; i++) bs.set(rng.nextInt(1 << k));
        return TruthTable.of(k, bs);
    }
}
