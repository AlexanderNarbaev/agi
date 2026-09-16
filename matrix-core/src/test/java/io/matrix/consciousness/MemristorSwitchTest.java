package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MemristorSwitchTest {

    @Test
    void conductanceIsMonotonicInW() {
        assertThat(MemristorSwitch.conductance(0)).isLessThan(MemristorSwitch.conductance(0.5));
        assertThat(MemristorSwitch.conductance(0.5)).isLessThan(MemristorSwitch.conductance(1.0));
    }

    @Test
    void conductanceBoundsAreCorrect() {
        assertThat(MemristorSwitch.conductance(0)).isEqualTo(1e-6);
        assertThat(MemristorSwitch.conductance(1)).isEqualTo(1.0);
    }

    @Test
    void resistanceBoundsAreCorrect() {
        assertThat(MemristorSwitch.resistance(1)).isEqualTo(1.0);
        // Off resistance is very high
        assertThat(MemristorSwitch.resistance(0)).isGreaterThan(1e5);
    }

    @Test
    void resistanceIsInverseOfConductance() {
        double w = 0.5;
        double g = MemristorSwitch.conductance(w);
        double r = MemristorSwitch.resistance(w);
        assertThat(r).isCloseTo(1.0 / g, within(1e-9));
    }

    @Test
    void simulateReturnsValidConductanceTrajectory() {
        double[] voltages = {0.5, -0.3, 0.8, -0.6, 0.1};
        double[] g = MemristorSwitch.simulate(voltages, 0.5, 1e-10);
        assertThat(g.length).isEqualTo(voltages.length + 1);
        for (double gi : g) {
            assertThat(gi).isBetween(1e-6, 1.0);
        }
    }

    @Test
    void simulateEmptyInputReturnsSingleConductance() {
        double[] g = MemristorSwitch.simulate(new double[0], 0.5, 1e-10);
        assertThat(g.length).isEqualTo(1);
        assertThat(g[0]).isCloseTo(MemristorSwitch.conductance(0.5), within(1e-9));
    }

    @Test
    void stdpUpdateClampsToZeroOne() {
        // Massive positive update should not exceed 1
        double w = MemristorSwitch.stdpUpdate(0.9, 50.0, 100.0);
        assertThat(w).isLessThanOrEqualTo(1.0);
        // Massive negative should not go below 0
        double w2 = MemristorSwitch.stdpUpdate(0.1, -50.0, 100.0);
        assertThat(w2).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void stdpPositivePrePostPotentiates() {
        // Δt > 0 (pre before post) with positive voltage: should increase w
        double w0 = 0.5;
        double w1 = MemristorSwitch.stdpUpdate(w0, 10.0, 0.5);
        assertThat(w1).isGreaterThan(w0);
    }

    @Test
    void stdpNegativePrePostDepresses() {
        // Δt < 0 (post before pre) with positive voltage: should decrease w
        double w0 = 0.5;
        double w1 = MemristorSwitch.stdpUpdate(w0, -10.0, 0.5);
        assertThat(w1).isLessThan(w0);
    }

    @Test
    void stdpNoChangeFarApart() {
        // Very large Δt → exp(-Δt/τ) → 0 → no change
        double w0 = 0.5;
        double w1 = MemristorSwitch.stdpUpdate(w0, 1000.0, 0.5);
        assertThat(w1).isCloseTo(w0, within(1e-9));
    }

    @Test
    void crossbarReturnsCorrectDimensions() {
        double[][] w = MemristorSwitch.simulateCrossbar(5, 7, 42L, new double[] {0.1, 0.2, 0.3});
        assertThat(w.length).isEqualTo(5);
        for (double[] row : w) {
            assertThat(row.length).isEqualTo(7);
        }
    }

    @Test
    void crossbarWeightsRemainInBounds() {
        double[][] w = MemristorSwitch.simulateCrossbar(4, 4, 99L, new double[] {0.5, 0.5, 0.5, 0.5});
        for (double[] row : w) {
            for (double v : row) {
                assertThat(v).isBetween(0.0, 1.0);
            }
        }
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
