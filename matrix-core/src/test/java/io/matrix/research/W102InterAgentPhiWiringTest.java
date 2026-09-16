package io.matrix.research;

import io.matrix.neuron.ConsciousBrain;
import io.matrix.research.PatternGenerator.Type;
import io.matrix.consciousness.InterAgentPhi;
import io.matrix.consciousness.InterAgentPhiSnapshot;

import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 102 — ConsciousBrain captures inter-agent snapshots and computes
 * InterAgentPhi over the recent window.
 */
class W102InterAgentPhiWiringTest {

    private static final int N_DIM = 1024;
    private static final int N_CYCLES = 40;

    @Test
    void snapshotsAreCapturedEachCycle() {
        ConsciousBrain brain = new ConsciousBrain(N_DIM, 42);
        Random rng = new Random(42);
        for (int c = 0; c < N_CYCLES; c++) {
            float[] obs = PatternGenerator.generate(Type.GAUSSIAN, N_DIM, rng);
            brain.cycle(obs);
        }
        int snapshotCount = brain.interAgentSnapshotCount();
        System.out.printf("W102: inter-agent snapshots after %d cycles = %d%n",
                N_CYCLES, snapshotCount);
        int expectedCount = Math.min(N_CYCLES, 32); // INTER_AGENT_SNAPSHOT_CAPACITY
        assertThat(snapshotCount).isEqualTo(expectedCount);
    }

    @Test
    void interAgentPhiComputed() {
        ConsciousBrain brain = new ConsciousBrain(N_DIM, 42);
        Random rng = new Random(42);
        for (int c = 0; c < N_CYCLES; c++) {
            float[] obs = PatternGenerator.generate(Type.GAUSSIAN, N_DIM, rng);
            brain.cycle(obs);
        }
        double phi = brain.interAgentPhi();
        System.out.printf("W102: inter-agent Φ after %d cycles = %.4f%n",
                N_CYCLES, phi);
        // Should be ≥ 0
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void interAgentPhiEmptyForShortRun() {
        ConsciousBrain brain = new ConsciousBrain(N_DIM, 42);
        // No cycles → no snapshots → Φ = 0 (not enough data)
        assertThat(brain.interAgentPhi()).isEqualTo(0.0);
    }

    @Test
    void interAgentPhiDeterministicSameSeed() {
        ConsciousBrain b1 = new ConsciousBrain(N_DIM, 42);
        ConsciousBrain b2 = new ConsciousBrain(N_DIM, 42);
        Random rng1 = new Random(42);
        Random rng2 = new Random(42);
        for (int c = 0; c < N_CYCLES; c++) {
            b1.cycle(PatternGenerator.generate(Type.GAUSSIAN, N_DIM, rng1));
            b2.cycle(PatternGenerator.generate(Type.GAUSSIAN, N_DIM, rng2));
        }
        assertThat(b1.interAgentPhi()).isCloseTo(b2.interAgentPhi(),
                org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void snapshotBuilderConstructsValidState() {
        // Direct test of InterAgentPhiSnapshot construction
        java.util.Random rng = new java.util.Random(42);
        float[] sr = new float[5];
        for (int i = 0; i < sr.length; i++) sr[i] = (float) rng.nextGaussian();
        // SelfModel.modelStep signature: observation, prediction, action, observation
        io.matrix.neuron.SelfModel.SelfModelResult sm = io.matrix.neuron.SelfModel.modelStep(
                sr, sr.clone(), sr.clone(), sr);
        io.matrix.neuron.WuWeiPolicy.Decision dec =
                io.matrix.neuron.WuWeiPolicy.decide(0.5, 0.3, 0);
        InterAgentPhiSnapshot snap = InterAgentPhiSnapshot.of(sm, null, dec, 1);
        assertThat(snap.stateVector()).hasSize(8);
        assertThat(snap.cycleCount()).isEqualTo(1);
        // Hash should be deterministic for same inputs
        InterAgentPhiSnapshot snap2 = InterAgentPhiSnapshot.of(sm, null, dec, 1);
        assertThat(snap.snapshotHash()).isEqualTo(snap2.snapshotHash());
    }
}
