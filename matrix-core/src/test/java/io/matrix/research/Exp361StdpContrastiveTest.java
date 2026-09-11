package io.matrix.research;

import io.matrix.neuron.ContrastiveNeuron;
import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.Neurotransmitter;
import io.matrix.neuron.StdpUpdate;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 361 — DESIGN-29/30 STDP + Contrastive Hebbian implementations.
 */
class Exp361StdpContrastiveTest {

    @Test
    void stdpPreBeforePostStrengthens() {
        // pre at t=0, post at t=10 (10ms after) → LTP
        double delta = StdpUpdate.deltaMagnitude(0, 10);
        assertThat(delta).isGreaterThan(0);  // LTP = positive
    }

    @Test
    void stdpPostBeforePreWeakens() {
        // pre at t=10, post at t=0 → LTD
        double delta = StdpUpdate.deltaMagnitude(10, 0);
        assertThat(delta).isLessThan(0);  // LTD = negative
    }

    @Test
    void stdpSimultaneousIsZero() {
        double delta = StdpUpdate.deltaMagnitude(5, 5);
        assertThat(delta).isEqualTo(0.0);
    }

    @Test
    void stdpMagnitudeDecayWithDt() {
        // Δmagnitude should decay with time difference
        double close = StdpUpdate.deltaMagnitude(0, 5);
        double far = StdpUpdate.deltaMagnitude(0, 50);
        assertThat(close).isGreaterThan(far);
    }

    @Test
    void stdpApplyUpdatesMagnitude() {
        EnrichedNeuron n = new EnrichedNeuron(makeTable(8, 50, 0xAL), 0.5,
                new double[]{0.5, 0.5, 0.5, 0.5}, Neurotransmitter.SEROTONIN);
        EnrichedNeuron strengthened = StdpUpdate.apply(n, 0, 5);
        assertThat(strengthened.magnitude()).isGreaterThan(n.magnitude());
        EnrichedNeuron weakened = StdpUpdate.apply(n, 5, 0);
        assertThat(weakened.magnitude()).isLessThan(n.magnitude());
    }

    @Test
    void contrastiveCorrectStrengthens() {
        EnrichedNeuron n = new EnrichedNeuron(makeTable(8, 50, 0xAL), 0.5,
                new double[]{0.5, 0.5, 0.5, 0.5}, Neurotransmitter.SEROTONIN);
        // fired=true, expected=true → correct → strengthen
        EnrichedNeuron updated = ContrastiveNeuron.contrastive(n, true, true, 0.05);
        assertThat(updated.magnitude()).isGreaterThan(n.magnitude());
    }

    @Test
    void contrastiveIncorrectWeakens() {
        EnrichedNeuron n = new EnrichedNeuron(makeTable(8, 50, 0xAL), 0.5,
                new double[]{0.5, 0.5, 0.5, 0.5}, Neurotransmitter.SEROTONIN);
        // fired=true, expected=false → incorrect → weaken
        EnrichedNeuron updated = ContrastiveNeuron.contrastive(n, true, false, 0.05);
        assertThat(updated.magnitude()).isLessThan(n.magnitude());
    }

    @Test
    void contrastiveMissedFireWeakens() {
        EnrichedNeuron n = new EnrichedNeuron(makeTable(8, 50, 0xAL), 0.5,
                new double[]{0.5, 0.5, 0.5, 0.5}, Neurotransmitter.SEROTONIN);
        // fired=false, expected=true → missed fire → weaken
        EnrichedNeuron updated = ContrastiveNeuron.contrastive(n, false, true, 0.05);
        assertThat(updated.magnitude()).isLessThan(n.magnitude());
    }

    private static TruthTable makeTable(int k, int cardinality, long seed) {
        BitSet bs = new BitSet(1 << k);
        Random rng = new Random(seed);
        for (int i = 0; i < cardinality; i++) bs.set(rng.nextInt(1 << k));
        return TruthTable.of(k, bs);
    }
}
