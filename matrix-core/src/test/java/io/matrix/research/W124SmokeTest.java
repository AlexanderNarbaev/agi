package io.matrix.research;

import io.matrix.neuron.ConsciousBrain;
import io.matrix.neuron.ConsciousBrain.CycleReport;
import io.matrix.consciousness.*;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W124 — Comprehensive smoke test for all W87-W123 surface.
 *
 * <p>Runs through every cross-disciplinary measurement subsystem,
 * verifying that each can be exercised in a single test flow.
 */
class W124SmokeTest {

    @Test
    void allMeasurementSubsystemsCoexist() {
        ConsciousBrain brain = new ConsciousBrain(1024, 42L);
        KolmogorovComplexityRecorder recorder = new KolmogorovComplexityRecorder();
        Random rng = new Random(123);
        float[] obs = new float[1024];
        for (int i = 0; i < 1024; i++) obs[i] = rng.nextFloat() * 2 - 1;

        // 1. Run brain
        CycleReport r = brain.cycle(obs);
        assertThat(r).isNotNull();

        // 2. Compute trajectory
        long[] traj = new long[32];
        for (int i = 0; i < 32; i++) traj[i] = (long) (i * 1000 + rng.nextInt(500));

        // 3. Kolmogorov snapshot (W104 + W116)
        recorder.capture(0, traj);
        KolmogorovComplexitySnapshot kcSnap = recorder.latest();
        assertThat(kcSnap.kolmogorovK()).isGreaterThan(0.0);

        // 4. Analogical consistency (W105)
        long[] another = new long[32];
        for (int i = 0; i < 32; i++) another[i] = (long) (i * 2000);
        double sim = AnalogicalConsistency.bitSimilarity(traj, another);
        assertThat(sim).isBetween(0.0, 1.0);

        // 5. Conceptual exclusion (W106)
        double excl = ConceptualExclusion.compositeExclusion(traj, another);
        assertThat(excl).isBetween(0.0, 1.0);

        // 6. NK Boolean network (W108)
        NKBooleanNetwork.CycleRecord nkRec = NKBooleanNetwork.findAttractor(
            8, 2, 42L, new boolean[]{true, false, true, false, true, false, true, false}, 100);
        assertThat(nkRec.converged()).isTrue();

        // 7. Memristor (W109)
        double[] memG = MemristorSwitch.simulate(new double[]{0.1, -0.1, 0.2}, 0.5, 1e-10);
        assertThat(memG.length).isEqualTo(4);

        // 8. L-system (W110)
        String lSystemOutput = LSystem.generate("F", LSystem.fractalPlant(), 3);
        assertThat(lSystemOutput).isNotEmpty();

        // 9. L-system complexity (W123)
        double lK = LSystemComplexity.generateAndMeasure("F", LSystem.fractalPlant(), 3);
        assertThat(lK).isGreaterThan(0.0);

        // 10. Cognitive Genesis Profile (W111 + W112 + W119)
        CognitiveGenesisProfile profile = CognitiveGenesisProfileBuilder.fromCycleReport(r, traj, null);
        CognitiveGenesisProfile profile2 = CognitiveGenesisProfileBuilder2.build(r, traj, traj, another);
        assertThat(profile).isNotNull();
        assertThat(profile2).isNotNull();
        assertThat(profile.unifiedComplexityScore()).isBetween(0.0, 1.0);
        assertThat(profile2.regime()).isIn("FROZEN", "EDGE_OF_CHAOS", "CHAOTIC");
    }

    @Test
    void allSubsystemsProduceDeterministicResults() {
        // Same seed → same results across subsystems
        long seed = 12345L;
        boolean[] init = new boolean[]{true, false, true, false, true, false, true, false};
        NKBooleanNetwork.CycleRecord r1 = NKBooleanNetwork.findAttractor(8, 2, seed, init, 100);
        NKBooleanNetwork.CycleRecord r2 = NKBooleanNetwork.findAttractor(8, 2, seed, init, 100);
        assertThat(r1.cycleLength()).isEqualTo(r2.cycleLength());

        long[] traj1 = {1L, 2L, 3L, 4L};
        long[] traj2 = {1L, 2L, 3L, 4L};
        double k1 = KolmogorovComplexity.estimate(traj1);
        double k2 = KolmogorovComplexity.estimate(traj2);
        assertThat(k1).isEqualTo(k2);

        double[][] mem1 = MemristorSwitch.simulateCrossbar(4, 4, seed, new double[]{0.1, 0.2});
        double[][] mem2 = MemristorSwitch.simulateCrossbar(4, 4, seed, new double[]{0.1, 0.2});
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                assertThat(mem1[i][j]).isCloseTo(mem2[i][j], within(1e-9));
            }
        }
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
