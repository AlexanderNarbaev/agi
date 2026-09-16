package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W144 — StabilityPhi property-based tests.
 */
class StabilityPhiPropertyTest {

    @Property(tries = 50)
    void propertyVarianceNonNegative(@ForAll("phiLists") List<Double> vals,
                                     @ForAll("windowSizes") int window) {
        if (window < 2 || vals.size() < window) return;
        double var = StabilityPhi.variance(vals, window);
        assertThat(var).isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 50)
    void propertyCoefficientOfVariationNonNegative(@ForAll("phiLists") List<Double> vals,
                                                    @ForAll("windowSizes") int window) {
        if (window < 2 || vals.size() < window) return;
        double cv = StabilityPhi.coefficientOfVariation(vals, window);
        assertThat(cv).isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyTrendBounded(@ForAll("phiLists") List<Double> vals,
                               @ForAll("windowSizes") int window) {
        if (window < 2 || vals.size() < window) return;
        double trend = StabilityPhi.trend(vals, window);
        assertThat(Double.isFinite(trend)).isTrue();
    }

    @Property(tries = 30)
    void propertyConstantPhiIsUltrastable(@ForAll("phiValues") double phi) {
        // All same value → variance=0 → ultrastable
        List<Double> vals = new ArrayList<>();
        for (int i = 0; i < 10; i++) vals.add(phi);
        boolean ultrastable = StabilityPhi.isUltrastable(vals, 5, 0.01);
        assertThat(ultrastable).isTrue();
    }

    @Property(tries = 30)
    void propertyRandomPhiIsNotUltrastable(@ForAll("anySeed") int seed) {
        Random rng = new Random(seed);
        List<Double> vals = new ArrayList<>();
        for (int i = 0; i < 10; i++) vals.add(rng.nextDouble() * 10);
        boolean ultrastable = StabilityPhi.isUltrastable(vals, 5, 0.01);
        // Random data with high variance → NOT ultrastable
        assertThat(ultrastable).isFalse();
    }

    @Provide
    Arbitrary<List<Double>> phiLists() {
        return Arbitraries.integers().between(4, 16).flatMap(n ->
            Arbitraries.doubles().between(0.0, 10.0).list().ofSize(n));
    }

    @Provide
    Arbitrary<Integer> windowSizes() {
        return Arbitraries.integers().between(2, 8);
    }

    @Provide
    Arbitrary<Double> phiValues() {
        return Arbitraries.doubles().between(0.0, 100.0);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }
}
