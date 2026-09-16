package io.matrix.consciousness;

import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W158 — CognitiveLyapunovExponent property-based tests.
 */
class CognitiveLyapunovExponentPropertyTest {

    @Property(tries = 50)
    void propertyIdenticalTrajectoriesZero(@ForAll("series") double[] t) {
        if (t.length < 2) return;
        // Identical trajectory → 0 (no divergence)
        double lambda = CognitiveLyapunovExponent.estimate(t, t);
        assertThat(lambda).isEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyEstimateIsFinite(@ForAll("series") double[] t1,
                                   @ForAll("series") double[] t2) {
        if (t1.length < 2 || t1.length != t2.length) return;
        double lambda = CognitiveLyapunovExponent.estimate(t1, t2);
        assertThat(Double.isFinite(lambda)).isTrue();
    }

    @Property(tries = 50)
    void propertyClassifyValidString(@ForAll("lambdas") double lambda) {
        String c = CognitiveLyapunovExponent.classify(lambda);
        assertThat(c).isIn("CHAOTIC", "STABLE", "EDGE_OF_CHAOS");
    }

    @Property(tries = 30)
    void propertyPositiveLambdaIsChaotic(@ForAll("positiveValues") double v) {
        if (v > 0.01) {
            assertThat(CognitiveLyapunovExponent.classify(v)).isEqualTo("CHAOTIC");
        }
    }

    @Property(tries = 30)
    void propertyNegativeLambdaIsStable(@ForAll("negativeValues") double v) {
        if (v < -0.01) {
            assertThat(CognitiveLyapunovExponent.classify(v)).isEqualTo("STABLE");
        }
    }

    @Property(tries = 30)
    void propertySmallLambdaIsEdge(@ForAll("smallValues") double v) {
        if (v >= -0.01 && v <= 0.01) {
            assertThat(CognitiveLyapunovExponent.classify(v)).isEqualTo("EDGE_OF_CHAOS");
        }
    }

    @Property(tries = 30)
    void propertyEstimateFromModelIsFinite(@ForAll("lengths") int length,
                                            @ForAll("divergenceValues") double div,
                                            @ForAll("seeds") long seed) {
        if (length < 4) return;
        double lambda = CognitiveLyapunovExponent.estimateFromModel(length, div, seed);
        assertThat(Double.isFinite(lambda)).isTrue();
    }

    @Provide
    Arbitrary<double[]> series() {
        return Arbitraries.integers().between(2, 32).flatMap(n ->
            Arbitraries.doubles().between(0.0, 1.0).array(double[].class).ofSize(n));
    }

    @Provide
    Arbitrary<Double> lambdas() {
        return Arbitraries.doubles().between(-10.0, 10.0);
    }

    @Provide
    Arbitrary<Double> positiveValues() {
        return Arbitraries.doubles().between(0.0, 10.0);
    }

    @Provide
    Arbitrary<Double> negativeValues() {
        return Arbitraries.doubles().between(-10.0, 0.0);
    }

    @Provide
    Arbitrary<Double> smallValues() {
        return Arbitraries.doubles().between(-0.01, 0.01);
    }

    @Provide
    Arbitrary<Integer> lengths() {
        return Arbitraries.integers().between(4, 64);
    }

    @Provide
    Arbitrary<Double> divergenceValues() {
        return Arbitraries.doubles().between(-1.0, 1.0);
    }

    @Provide
    Arbitrary<Long> seeds() {
        return Arbitraries.longs().between(0, Long.MAX_VALUE / 2);
    }
}
