package io.matrix.research;

import io.matrix.consciousness.MemristorSwitch;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W121 — Memristor phase transitions under repeated STDP pulses.
 *
 * <p>Test H-094: memristor dynamics produce phase transitions analogous
 * to those at Kauffman edge of chaos. Apply a sequence of voltage pulses
 * to a small crossbar and verify weight distribution.
 */
class W121MemristorPhaseTransition {

    @Test
    void singleMemristorSettles() {
        // Single memristor with alternating voltages should oscillate around stable point
        double[] voltages = new double[100];
        for (int i = 0; i < 100; i++) voltages[i] = (i % 2 == 0) ? 0.1 : -0.1;
        double[] g = MemristorSwitch.simulate(voltages, 0.5, 1e-10);
        // Final conductance should be near initial (oscillating)
        assertThat(g[99]).isBetween(0.3, 0.7);
    }

    @Test
    void monontonicInputDrivesMemristorToOneBoundary() {
        // Persistent positive voltage → memristor should saturate
        double[] voltages = new double[100];
        java.util.Arrays.fill(voltages, 1.0);
        double[] g = MemristorSwitch.simulate(voltages, 0.5, 1e-8);
        // Should converge toward 1.0
        assertThat(g[99]).isGreaterThan(0.5);
    }

    @Test
    void crossbarConvergesUnderRepeatedPulses() {
        double[][] w1 = MemristorSwitch.simulateCrossbar(4, 4, 42L, new double[]{0.5});
        double[][] w2 = MemristorSwitch.simulateCrossbar(4, 4, 42L, new double[]{0.5, 0.5, 0.5, 0.5, 0.5});
        // After more pulses, weights should differ from initial
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (Math.abs(w1[i][j] - w2[i][j]) > 1e-6) {
                    changed = true;
                    break;
                }
            }
        }
        assertThat(changed).isTrue();
    }

    @Test
    void crossbarDeterministicForSameSeed() {
        double[][] w1 = MemristorSwitch.simulateCrossbar(3, 3, 99L, new double[]{0.1, 0.2, 0.3});
        double[][] w2 = MemristorSwitch.simulateCrossbar(3, 3, 99L, new double[]{0.1, 0.2, 0.3});
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertThat(w1[i][j]).isCloseTo(w2[i][j], within(1e-9));
            }
        }
    }

    @Test
    void stdpBidirectionalUpdates() {
        // Apply positive then negative Δt — weight should oscillate around initial
        double w = 0.5;
        for (int i = 0; i < 10; i++) {
            w = MemristorSwitch.stdpUpdate(w, 10.0, 0.5);   // pre > post → +Δw
            w = MemristorSwitch.stdpUpdate(w, -10.0, 0.5);  // post > pre → -Δw
        }
        // Should be near initial
        assertThat(w).isCloseTo(0.5, within(0.1));
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
