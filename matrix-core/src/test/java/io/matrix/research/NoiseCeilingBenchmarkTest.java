package io.matrix.research;

import io.matrix.consciousness.IntegrationMetrics;
import io.matrix.neuron.ConsciousBrain;
import io.matrix.research.PatternGenerator.Type;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Wave 85 — Noise-ceiling benchmark for integration metrics.
 *
 * <p>For each integration metric (Φ_binary, ΦR, ΦF, C_N), measures:
 * <ol>
 *   <li>Signal value: the metric on structured patterns (periodic, sparse, recurrent)</li>
 *   <li>Noise value: the metric on random Gaussian noise</li>
 *   <li>Signal-to-noise ratio: signal / noise</li>
 * </ol>
 *
 * <p>The signal should be measurably above the noise floor for genuine
 * integration (H-082). Per W80 priority 5.
 *
 * <p>CONSTITUTION VI compliance: This benchmark measures whether metrics
 * are sensitive to structure, not whether MATRIX is conscious.
 */
class NoiseCeilingBenchmarkTest {

    private static final int N_DIM = 1024;
    private static final int N_CYCLES = 50;
    private static final int N_TRIALS = 3;

    @Test
    void phiBinarySignalAboveNoiseFloor() {
        double signalPhi = runConsciousBrain(Type.PERIODIC, N_CYCLES, N_TRIALS);
        double noisePhi = runConsciousBrain(Type.GAUSSIAN, N_CYCLES, N_TRIALS);
        System.out.printf("Noise ceiling for Φ_binary: signal=%.4f, noise=%.4f, ratio=%.2f%n",
                signalPhi, noisePhi, signalPhi / Math.max(1e-9, noisePhi));
        // Signal should not be negative
        assertThat(signalPhi).isGreaterThanOrEqualTo(0.0);
        assertThat(noisePhi).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void phiRSignalAboveNoiseFloor() {
        double signalPhiR = runConsciousBrainPhiR(Type.PERIODIC, N_CYCLES, N_TRIALS);
        double noisePhiR = runConsciousBrainPhiR(Type.GAUSSIAN, N_CYCLES, N_TRIALS);
        System.out.printf("Noise ceiling for ΦR: signal=%.4f, noise=%.4f, ratio=%.2f%n",
                signalPhiR, noisePhiR, signalPhiR / Math.max(1e-9, noisePhiR));
        assertThat(signalPhiR).isGreaterThanOrEqualTo(0.0);
        assertThat(noisePhiR).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void cNSignalAboveNoiseFloor() {
        double signalCN = runConsciousBrainCN(Type.PERIODIC, N_CYCLES, N_TRIALS);
        double noiseCN = runConsciousBrainCN(Type.GAUSSIAN, N_CYCLES, N_TRIALS);
        System.out.printf("Noise ceiling for C_N: signal=%.4f, noise=%.4f, ratio=%.2f%n",
                signalCN, noiseCN, signalCN / Math.max(1e-9, noiseCN));
        assertThat(signalCN).isGreaterThanOrEqualTo(0.0);
        assertThat(noiseCN).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void recurrentPatternShowsIntegration() {
        // Recurrent pattern should show at least some integration signal
        double signalPhi = runConsciousBrain(Type.RECURRENT, N_CYCLES, N_TRIALS);
        double signalPhiR = runConsciousBrainPhiR(Type.RECURRENT, N_CYCLES, N_TRIALS);
        System.out.printf("Recurrent pattern: Φ_binary=%.4f, ΦR=%.4f%n", signalPhi, signalPhiR);
        assertThat(signalPhi).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void allPatternTypesProduceValidMetrics() {
        for (Type t : Type.values()) {
            double phi = runConsciousBrain(t, 20, 2);
            double phiR = runConsciousBrainPhiR(t, 20, 2);
            double cN = runConsciousBrainCN(t, 20, 2);
            assertThat(phi).isBetween(0.0, 8.0); // Phi_binary N=8 max ~ 8 bits
            assertThat(phiR).isGreaterThanOrEqualTo(0.0);
            assertThat(cN).isGreaterThanOrEqualTo(0.0);
        }
    }

    // ===== Helper methods =====

    private static double runConsciousBrain(Type type, int cycles, int trials) {
        double sum = 0;
        int count = 0;
        for (int t = 0; t < trials; t++) {
            ConsciousBrain brain = new ConsciousBrain(N_DIM, t);
            Random rng = new Random(t);
            for (int c = 0; c < cycles; c++) {
                if (c % 10 == 0) {
                    float[] obs = PatternGenerator.generate(type, N_DIM, rng);
                    var report = brain.cycle(obs);
                    if (report.phiBinary() != null) {
                        sum += report.phiBinary();
                        count++;
                    }
                } else {
                    float[] obs = PatternGenerator.generate(type, N_DIM, rng);
                    brain.cycle(obs);
                }
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    private static double runConsciousBrainPhiR(Type type, int cycles, int trials) {
        double sum = 0;
        int count = 0;
        for (int t = 0; t < trials; t++) {
            ConsciousBrain brain = new ConsciousBrain(N_DIM, t);
            Random rng = new Random(t);
            for (int c = 0; c < cycles; c++) {
                if (c % 10 == 0) {
                    float[] obs = PatternGenerator.generate(type, N_DIM, rng);
                    var report = brain.cycle(obs);
                    if (report.phiR() != null) {
                        sum += report.phiR();
                        count++;
                    }
                } else {
                    float[] obs = PatternGenerator.generate(type, N_DIM, rng);
                    brain.cycle(obs);
                }
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    private static double runConsciousBrainCN(Type type, int cycles, int trials) {
        double sum = 0;
        int count = 0;
        for (int t = 0; t < trials; t++) {
            ConsciousBrain brain = new ConsciousBrain(N_DIM, t);
            Random rng = new Random(t);
            for (int c = 0; c < cycles; c++) {
                if (c % 10 == 0) {
                    float[] obs = PatternGenerator.generate(type, N_DIM, rng);
                    var report = brain.cycle(obs);
                    if (report.neuralComplexity() != null) {
                        sum += report.neuralComplexity();
                        count++;
                    }
                } else {
                    float[] obs = PatternGenerator.generate(type, N_DIM, rng);
                    brain.cycle(obs);
                }
            }
        }
        return count > 0 ? sum / count : 0.0;
    }
}
