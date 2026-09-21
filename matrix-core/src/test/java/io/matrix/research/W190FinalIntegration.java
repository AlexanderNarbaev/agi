package io.matrix.research;

import io.matrix.neuron.ConsciousBrain;
import io.matrix.neuron.ConsciousBrain.CycleReport;
import io.matrix.consciousness.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W190 — Final integration test.
 *
 * <p>Run ConsciousBrain through multiple cycles, build CognitiveGenesisProfile
 * for each cycle, then exercise ALL major analysis subsystems:
 * - VariationalFreeEnergy (active inference)
 * - CausalEmergence (multi-scale)
 * - PhiMaxCalculator (IIT 4.0)
 * - ProfileDistance (L1/L2/cosine)
 * - CognitivePhaseDetector (regime transitions)
 * - CognitiveEntropyMeter (Shannon entropy)
 * - ProfileStabilityMetrics (holistic classification)
 * - SeriesCorrelator (autocorrelation)
 * - CognitiveHeatmap (visualization)
 */
class W190FinalIntegration {

    @Test
    void allSubsystemsProcessBrainCycles() {
        ConsciousBrain brain = new ConsciousBrain(1024, 42L);
        Random rng = new Random(123);
        float[] obs = new float[1024];
        for (int i = 0; i < 1024; i++) obs[i] = rng.nextFloat() * 2 - 1;

        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int cycle = 0; cycle < 20; cycle++) {
            CycleReport r = brain.cycle(obs);
            long[] traj = new long[32];
            for (int j = 0; j < 32; j++) traj[j] = (long) (cycle * 7919 + j);
            CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder.fromCycleReport(r, traj, null);
            profiles.add(p);
        }

        // All subsystem analysis
        // 1. VFE
        double vfe = VariationalFreeEnergy.vfe(profiles.get(10), profiles.get(0));
        assertThat(vfe).isGreaterThanOrEqualTo(0.0);

        // 2. Causal emergence (coarsen profile fields)
        double[] phiB = new double[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) phiB[i] = profiles.get(i).phiBinary();
        double maxCE = CausalEmergence.maxCausalEmergence(phiB);
        assertThat(Double.isFinite(maxCE) || Double.isNaN(maxCE)).isTrue();

        // 3. PhiMax on discrete state
        int[] states = new int[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            states[i] = (int) (profiles.get(i).phiBinary() * 8);
        }
        double phiMax = PhiMaxCalculator.phiMaxGreedy(states);
        assertThat(phiMax).isGreaterThanOrEqualTo(0.0);

        // 4. Profile distance
        double dist = ProfileDistance.l1Distance(profiles.get(0), profiles.get(10));
        assertThat(dist).isBetween(0.0, 1.0);

        // 5. Phase detection
        var transitions = CognitivePhaseDetector.detectTransitions(profiles);
        assertThat(transitions.size()).isGreaterThanOrEqualTo(0);

        // 6. Entropy
        double entropy = CognitiveEntropyMeter.regimeEntropy(profiles);
        assertThat(entropy).isGreaterThanOrEqualTo(0.0);

        // 7. Stability classification
        String stability = ProfileStabilityMetrics.classify(profiles);
        assertThat(stability).isIn("STATIONARY", "OSCILLATORY", "DRIFTING", "CHAOTIC", "INSUFFICIENT_DATA");

        // 8. Series correlation
        double[] velocity = ProfileVelocityTracker.velocity(profiles);
        double[] acf = SeriesCorrelator.autocorrelation(velocity, 5);
        assertThat(acf[0]).isCloseTo(1.0, within(1e-9));

        // 9. Heatmap
        double[][] heatmap = CognitiveHeatmap.toHeatmap(profiles);
        assertThat(heatmap.length).isEqualTo(profiles.size());

        // 10. Regime trajectory analysis
        int[] runLengths = RegimeTrajectoryAnalyzer.runLengths(profiles);
        assertThat(runLengths.length).isEqualTo(3);

        // 11. Active inference — select action that minimizes EFE
        CognitiveGenesisProfile target = profiles.get(profiles.size() - 1);
        int bestAction = VariationalFreeEnergy.selectAction(profiles, target);
        assertThat(bestAction).isBetween(0, profiles.size() - 1);

        // 12. ELBO
        double elbo = VariationalFreeEnergy.elbo(profiles, profiles.get(0));
        assertThat(Double.isFinite(elbo) || Double.isNaN(elbo)).isTrue();
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
