package io.matrix.consciousness;

import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W185 — CausalEmergence property-based tests.
 */
class CausalEmergencePropertyTest {

    @Property(tries = 50)
    void propertyEIUniformIsZero(@ForAll("sizes") int n) {
        if (n < 2) return;
        double[] uniform = new double[n];
        for (int i = 0; i < n; i++) uniform[i] = 1.0;
        double ei = CausalEmergence.effectiveInformation(uniform);
        assertThat(ei).isCloseTo(0.0, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyEIDeltaIsLogN(@ForAll("sizes") int n) {
        if (n < 2) return;
        double[] delta = new double[n];
        delta[0] = 1.0;
        double ei = CausalEmergence.effectiveInformation(delta);
        double expected = Math.log(n) / Math.log(2);
        assertThat(ei).isCloseTo(expected, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyEIOfNonNegativeDistribution(@ForAll("distributions") double[] dist) {
        double ei = CausalEmergence.effectiveInformation(dist);
        if (!Double.isNaN(ei)) {
            assertThat(ei).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Property(tries = 50)
    void propertyCausalEmergenceIdenticalIsZero(@ForAll("distributions") double[] dist) {
        double ce = CausalEmergence.causalEmergence(dist, dist);
        assertThat(ce).isCloseTo(0.0, Assertions.offset(1e-9));
    }

    @Property(tries = 30)
    void propertyCoarsenReducesSize(@ForAll("sizes") int n,
                                     @ForAll("binSizes") int binSize) {
        if (n < 1 || binSize < 1) return;
        double[] micro = new double[n];
        for (int i = 0; i < n; i++) micro[i] = i + 1;
        double[] macro = CausalEmergence.coarsenByBinning(micro, binSize);
        assertThat(macro.length).isLessThanOrEqualTo((n + binSize - 1) / binSize);
        // Sum should equal sum of micro (if bins don't overflow)
        double sumMicro = 0, sumMacro = 0;
        for (double v : micro) sumMicro += v;
        for (double v : macro) sumMacro += v;
        assertThat(sumMacro).isCloseTo(sumMicro, Assertions.offset(1e-9));
    }

    @Property(tries = 30)
    void propertyMaxCausalEmergenceForUniformIsZero(@ForAll("sizes") int n) {
        if (n < 4) return;
        double[] uniform = new double[n];
        for (int i = 0; i < n; i++) uniform[i] = 1.0;
        double maxCE = CausalEmergence.maxCausalEmergence(uniform);
        assertThat(maxCE).isCloseTo(0.0, Assertions.offset(1e-9));
    }

    @Provide
    Arbitrary<double[]> distributions() {
        return Arbitraries.integers().between(2, 16).flatMap(n ->
            Arbitraries.doubles().between(0.01, 1.0).array(double[].class).ofSize(n));
    }

    @Provide
    Arbitrary<Integer> sizes() {
        return Arbitraries.integers().between(2, 16);
    }

    @Provide
    Arbitrary<Integer> binSizes() {
        return Arbitraries.integers().between(1, 8);
    }

    private static class Assertions {
        static org.assertj.core.data.Offset<Double> offset(double tol) {
            return org.assertj.core.data.Offset.offset(tol);
        }
    }
}
