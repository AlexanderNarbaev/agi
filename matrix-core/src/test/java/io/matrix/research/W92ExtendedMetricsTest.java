package io.matrix.research;

import io.matrix.consciousness.ExtendedIntegrationMetrics;
import io.matrix.neuron.ConsciousBrain;
import io.matrix.neuron.ConsciousBrain.CycleReport;
import io.matrix.research.PatternGenerator.Type;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 92 — ConsciousBrain emits extended integration metrics (Φ_linGauss + PhiID 4-atom).
 *
 * <p>ConsciousBrain now publishes both discrete Φ_binary/ΦR/ΦF/C_N/ticklingFlag (every
 * cycle) and continuous Φ_linGauss/PhiID (every EXTENDED_EVERY=10 cycles) on the
 * {@link io.matrix.consciousness.ExtendedIntegrationMetrics} field of
 * {@link CycleReport}.
 *
 * <p>CONSTITUTION VI compliance: continuous metrics remain information-theoretic
 * measurements, not phenomenal consciousness claims.
 */
class W92ExtendedMetricsTest {

    private static final int N_DIM = 1024;
    private static final int N_CYCLES = 30;

    @Test
    void extendedMetricsEmittedAtCadence() {
        ConsciousBrain brain = new ConsciousBrain(N_DIM, 42);
        Random rng = new Random(42);
        int extendedEmitted = 0;
        int extendedNull = 0;
        double phiLinGaussSum = 0;
        int phiLinGaussCount = 0;
        double phiIdRSum = 0;
        int phiIdRCount = 0;
        double phiIdSSum = 0;
        int phiIdSCount = 0;
        for (int c = 0; c < N_CYCLES; c++) {
            float[] obs = PatternGenerator.generate(Type.GAUSSIAN, N_DIM, rng);
            CycleReport r = brain.cycle(obs);
            ExtendedIntegrationMetrics ext = r.extended();
            if (ext == null) {
                extendedNull++;
            } else {
                extendedEmitted++;
                if (ext.phiLinGauss() != null) {
                    phiLinGaussSum += ext.phiLinGauss();
                    phiLinGaussCount++;
                }
                if (ext.phiIdRedundancy() != null) {
                    phiIdRSum += ext.phiIdRedundancy();
                    phiIdRCount++;
                }
                if (ext.phiIdSynergy() != null) {
                    phiIdSSum += ext.phiIdSynergy();
                    phiIdSCount++;
                }
            }
        }
        System.out.printf("W92: extendedEmitted=%d, extendedNull=%d%n",
                extendedEmitted, extendedNull);
        System.out.printf("W92: Φ_linGauss mean=%.4f (count=%d)%n",
                phiLinGaussSum / Math.max(1, phiLinGaussCount), phiLinGaussCount);
        System.out.printf("W92: PhiID r=%.4f (count=%d), s=%.4f (count=%d)%n",
                phiIdRSum / Math.max(1, phiIdRCount), phiIdRCount,
                phiIdSSum / Math.max(1, phiIdSCount), phiIdSCount);
        // Cadence: extended emitted every 10 cycles (cycleCount > 0 && % 10 == 0).
        // For N_CYCLES=30: emissions at cycles 10, 20 → 2 emissions (but only if not null).
        // Plus possibly cycle 30 (boundary).
        assertThat(extendedEmitted).isGreaterThanOrEqualTo(2);
        // Discrete metrics always emitted at c>=2
        assertThat(phiLinGaussCount).isGreaterThanOrEqualTo(2);
    }

    @Test
    void extendedMetricsNonNegative() {
        ConsciousBrain brain = new ConsciousBrain(N_DIM, 42);
        Random rng = new Random(42);
        for (int c = 0; c < N_CYCLES; c++) {
            float[] obs = PatternGenerator.generate(Type.GAUSSIAN, N_DIM, rng);
            CycleReport r = brain.cycle(obs);
            ExtendedIntegrationMetrics ext = r.extended();
            if (ext != null) {
                if (ext.phiLinGauss() != null) {
                    assertThat(ext.phiLinGauss()).isGreaterThanOrEqualTo(0.0);
                }
                if (ext.phiIdRedundancy() != null) {
                    assertThat(ext.phiIdRedundancy()).isGreaterThanOrEqualTo(0.0);
                }
                if (ext.phiIdSynergy() != null) {
                    assertThat(ext.phiIdSynergy()).isGreaterThanOrEqualTo(0.0);
                }
                if (ext.phiIdUnqX() != null) {
                    assertThat(ext.phiIdUnqX()).isGreaterThanOrEqualTo(0.0);
                }
                if (ext.phiIdUnqY() != null) {
                    assertThat(ext.phiIdUnqY()).isGreaterThanOrEqualTo(0.0);
                }
            }
        }
    }

    @Test
    void extendedMetricsAcrossPatternTypes() {
        // Verify extended metrics are produced for multiple pattern types
        Type[] types = {Type.PERIODIC, Type.GAUSSIAN, Type.SPARSE};
        for (Type t : types) {
            ConsciousBrain brain = new ConsciousBrain(N_DIM, 42);
            Random rng = new Random(42);
            int extendedCount = 0;
            for (int c = 0; c < N_CYCLES; c++) {
                float[] obs = PatternGenerator.generate(t, N_DIM, rng);
                CycleReport r = brain.cycle(obs);
                if (r.extended() != null) extendedCount++;
            }
            System.out.printf("W92: Type %s extended emissions=%d%n", t, extendedCount);
            assertThat(extendedCount).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void discreteAndExtendedCoexist() {
        // Verify both discrete and extended metrics are emitted in the same cycle
        ConsciousBrain brain = new ConsciousBrain(N_DIM, 42);
        Random rng = new Random(42);
        for (int c = 0; c < N_CYCLES; c++) {
            float[] obs = PatternGenerator.generate(Type.GAUSSIAN, N_DIM, rng);
            CycleReport r = brain.cycle(obs);
            // When extended is emitted, discrete should also be present
            if (r.extended() != null) {
                assertThat(r.phiBinary()).isNotNull();
                assertThat(r.phiR()).isNotNull();
            }
        }
    }
}
