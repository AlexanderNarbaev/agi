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
 * W172 — End-to-end integration test exercising all W149-W171
 * measurement subsystems with actual ConsciousBrain cycles.
 */
class W172EndToEndIntegration {

    @Test
    void fullPipelineProducesConsistentResults() {
        ConsciousBrain brain = new ConsciousBrain(1024, 42L);
        Random rng = new Random(123);
        float[] obs = new float[1024];
        for (int i = 0; i < 1024; i++) obs[i] = rng.nextFloat() * 2 - 1;

        KolmogorovComplexityRecorder recorder = new KolmogorovComplexityRecorder();
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();

        for (int cycle = 0; cycle < 20; cycle++) {
            CycleReport r = brain.cycle(obs);
            long[] traj = new long[32];
            for (int i = 0; i < 32; i++) traj[i] = (long) (cycle * 7919 + i * 31);

            // W104, W116
            recorder.capture(cycle, traj);

            // W111, W112
            CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder.fromCycleReport(
                r, traj, null);
            profiles.add(p);

            // W164 — multivariate analysis on phi values
            double[][] phiMatrix = {{p.phiBinary(), p.phiF(), p.phiR()}};
            double[] means = MultivariateGaussianAnalyzer.mean(phiMatrix);
            assertThat(means).hasSize(3);
        }

        // W153 — phase detection
        List<CognitivePhaseDetector.PhaseTransition> transitions =
            CognitivePhaseDetector.detectTransitions(profiles);
        // May or may not have transitions — just verify it runs

        // W155 — entropy
        double regimeEntropy = CognitiveEntropyMeter.regimeEntropy(profiles);
        assertThat(regimeEntropy).isGreaterThanOrEqualTo(0.0);

        // W157 — Lyapunov (use velocity as proxy)
        double[] velocity = ProfileVelocityTracker.velocity(profiles);
        assertThat(velocity).hasSize(profiles.size() - 1);

        // W166 — heatmap
        double[][] heatmap = CognitiveHeatmap.toHeatmap(profiles);
        assertThat(heatmap.length).isEqualTo(profiles.size());
        assertThat(heatmap[0].length).isEqualTo(CognitiveHeatmap.fieldCount());

        // W168 — velocity tracker
        double maxV = ProfileVelocityTracker.maxVelocity(profiles);
        assertThat(maxV).isGreaterThanOrEqualTo(0.0);

        // W170 — stability classification
        String stability = ProfileStabilityMetrics.classify(profiles);
        assertThat(stability).isIn("STATIONARY", "OSCILLATORY", "DRIFTING", "CHAOTIC", "INSUFFICIENT_DATA");
    }

    @Test
    void measureSubsystemsWorkOnSyntheticProfiles() {
        // Create 50 synthetic profiles
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        Random rng = new Random(42);
        for (int i = 0; i < 50; i++) {
            double phi = rng.nextDouble();
            profiles.add(new CognitiveGenesisProfile(
                phi, phi, phi, phi, phi, rng.nextDouble(), phi,
                rng.nextDouble() * 100.0,
                rng.nextDouble(), rng.nextDouble(),
                rng.nextInt(8), rng.nextDouble(),
                rng.nextDouble() * 5.0
            ));
        }

        // Run all subsystem measurements
        double[][] heatmap = CognitiveHeatmap.toHeatmap(profiles);
        assertThat(heatmap).isNotEmpty();

        double[] velocity = ProfileVelocityTracker.velocity(profiles);
        assertThat(velocity).hasSize(49);

        double entropy = CognitiveEntropyMeter.regimeEntropy(profiles);
        assertThat(entropy).isBetween(0.0, 1.1);  // log(3) ≈ 1.099

        double meanV = ProfileVelocityTracker.meanVelocity(profiles);
        assertThat(meanV).isGreaterThanOrEqualTo(0.0);

        double stability = ProfileStabilityMetrics.stabilityScore(profiles);
        assertThat(stability).isBetween(0.0, 1.0);

        // SeriesCorrelator on velocity time series
        double[] acf = SeriesCorrelator.autocorrelation(velocity, 5);
        assertThat(acf[0]).isCloseTo(1.0, within(1e-9));
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
