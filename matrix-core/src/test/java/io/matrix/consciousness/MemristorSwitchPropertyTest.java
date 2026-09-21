package io.matrix.consciousness;

import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W133 — Memristor switch property-based tests.
 *
 * <p>Property-based verification of W109 MemristorSwitch.
 */
class MemristorSwitchPropertyTest {

    @Property(tries = 50)
    void propertyConductanceMonotonicInW(@ForAll("dopedFractions") double w) {
        // Conductance should be monotonic in doped-region fraction
        double gw = MemristorSwitch.conductance(w);
        if (w > 0.01) {
            double gwLower = MemristorSwitch.conductance(Math.max(0, w - 0.01));
            assertThat(gw).isGreaterThanOrEqualTo(gwLower);
        }
    }

    @Property(tries = 50)
    void propertyConductanceBounded(@ForAll("dopedFractions") double w) {
        double gw = MemristorSwitch.conductance(w);
        assertThat(gw).isBetween(1e-6, 1.0);
    }

    @Property(tries = 50)
    void propertyResistanceInverseOfConductance(@ForAll("dopedFractions") double w) {
        if (w <= 0 || w >= 1) return;  // skip extreme values
        double g = MemristorSwitch.conductance(w);
        double r = MemristorSwitch.resistance(w);
        assertThat(r).isCloseTo(1.0 / g, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyStdpClampsToZeroOne(@ForAll("dopedFractions") double w,
                                     @ForAll("deltaTimes") double dt,
                                     @ForAll("voltages") double v) {
        double newW = MemristorSwitch.stdpUpdate(w, dt, v);
        assertThat(newW).isBetween(0.0, 1.0);
    }

    @Property(tries = 30)
    void propertyStdpLargeDtNoChange(@ForAll("dopedFractions") double w) {
        // Far-apart pre/post → exp(-|Δt|/τ) → 0 → no change
        double newW = MemristorSwitch.stdpUpdate(w, 1000.0, 0.5);
        assertThat(newW).isEqualTo(w);
    }

    @Property(tries = 30)
    void propertyStdpPositivePrePostPotentiates(@ForAll("dopedFractions") double w) {
        if (w >= 0.95) return; // would saturate
        double newW = MemristorSwitch.stdpUpdate(w, 10.0, 0.5);
        assertThat(newW).isGreaterThan(w);
    }

    @Property(tries = 30)
    void propertyStdpNegativePrePostDepresses(@ForAll("dopedFractions") double w) {
        if (w <= 0.05) return; // would saturate to 0
        double newW = MemristorSwitch.stdpUpdate(w, -10.0, 0.5);
        assertThat(newW).isLessThan(w);
    }

    @Property(tries = 20)
    void propertyCrossbarWeightsBounded(@ForAll("sizes") int N,
                                         @ForAll("sizes") int M,
                                         @ForAll("seeds") long seed) {
        double[][] w = MemristorSwitch.simulateCrossbar(N, M, seed, new double[]{0.1, 0.2, 0.3});
        assertThat(w.length).isEqualTo(N);
        for (double[] row : w) {
            assertThat(row.length).isEqualTo(M);
            for (double v : row) {
                assertThat(v).isBetween(0.0, 1.0);
            }
        }
    }

    @Provide
    Arbitrary<Double> dopedFractions() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }

    @Provide
    Arbitrary<Double> deltaTimes() {
        return Arbitraries.doubles().between(-100.0, 100.0);
    }

    @Provide
    Arbitrary<Double> voltages() {
        return Arbitraries.doubles().between(-5.0, 5.0);
    }

    @Provide
    Arbitrary<Integer> sizes() {
        return Arbitraries.integers().between(1, 8);
    }

    @Provide
    Arbitrary<Long> seeds() {
        return Arbitraries.longs().between(0, Long.MAX_VALUE / 2);
    }

    // Provide static offset method since Assertions.offset may not be available
    private static class Assertions {
        static org.assertj.core.data.Offset<Double> offset(double tol) {
            return org.assertj.core.data.Offset.offset(tol);
        }
    }
}
