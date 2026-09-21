package io.matrix.consciousness;

import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W160 — InferentialDistance property-based tests.
 */
class InferentialDistancePropertyTest {

    @Property(tries = 50)
    void propertyJSSymmetric(@ForAll("distributions") double[][] pair) {
        if (pair.length < 2 || pair[0].length != pair[1].length) return;
        double js1 = InferentialDistance.jsDivergence(pair[0], pair[1]);
        double js2 = InferentialDistance.jsDivergence(pair[1], pair[0]);
        assertThat(js1).isCloseTo(js2, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyJSBounded(@ForAll("distributions") double[][] pair) {
        if (pair.length < 2 || pair[0].length != pair[1].length) return;
        double js = InferentialDistance.jsDivergence(pair[0], pair[1]);
        assertThat(js).isBetween(0.0, 1.0);
    }

    @Property(tries = 50)
    void propertyHellingerSymmetric(@ForAll("distributions") double[][] pair) {
        if (pair.length < 2 || pair[0].length != pair[1].length) return;
        double h1 = InferentialDistance.hellingerDistance(pair[0], pair[1]);
        double h2 = InferentialDistance.hellingerDistance(pair[1], pair[0]);
        assertThat(h1).isCloseTo(h2, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyHellingerBounded(@ForAll("distributions") double[][] pair) {
        if (pair.length < 2 || pair[0].length != pair[1].length) return;
        double h = InferentialDistance.hellingerDistance(pair[0], pair[1]);
        assertThat(h).isBetween(0.0, 1.0);
    }

    @Property(tries = 50)
    void propertyTVSymmetric(@ForAll("distributions") double[][] pair) {
        if (pair.length < 2 || pair[0].length != pair[1].length) return;
        double tv1 = InferentialDistance.totalVariationDistance(pair[0], pair[1]);
        double tv2 = InferentialDistance.totalVariationDistance(pair[1], pair[0]);
        assertThat(tv1).isCloseTo(tv2, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyTVBounded(@ForAll("distributions") double[][] pair) {
        if (pair.length < 2 || pair[0].length != pair[1].length) return;
        double tv = InferentialDistance.totalVariationDistance(pair[0], pair[1]);
        assertThat(tv).isBetween(0.0, 1.0);
    }

    @Property(tries = 50)
    void propertyIdenticalKLIsZero(@ForAll("distributions") double[] p) {
        double kl = InferentialDistance.klDivergence(p, p);
        // KL(P || P) = 0 (or infinity if P has zeros)
        if (!Double.isInfinite(kl)) {
            assertThat(kl).isEqualTo(0.0);
        }
    }

    @Property(tries = 50)
    void propertyCompositeBounded(@ForAll("distributions") double[][] pair) {
        if (pair.length < 2 || pair[0].length != pair[1].length) return;
        double c = InferentialDistance.compositeDistance(pair[0], pair[1]);
        assertThat(c).isBetween(0.0, 1.0);
    }

    @Provide
    Arbitrary<double[][]> distributions() {
        return Arbitraries.integers().between(2, 8).flatMap(n ->
            Arbitraries.doubles().between(0.01, 1.0).array(double[].class).ofSize(n).flatMap(p1 ->
                Arbitraries.doubles().between(0.01, 1.0).array(double[].class).ofSize(n)
                    .map(p2 -> new double[][]{normalize(p1), normalize(p2)})));
    }

    @Provide
    Arbitrary<double[]> distribution() {
        return Arbitraries.integers().between(2, 8).flatMap(n ->
            Arbitraries.doubles().between(0.01, 1.0).array(double[].class).ofSize(n)
                .map(InferentialDistancePropertyTest::normalize));
    }

    private static double[] normalize(double[] arr) {
        double sum = 0;
        for (double v : arr) sum += v;
        if (sum == 0) {
            double[] u = new double[arr.length];
            for (int i = 0; i < arr.length; i++) u[i] = 1.0 / arr.length;
            return u;
        }
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) result[i] = arr[i] / sum;
        return result;
    }

    private static class Assertions {
        static org.assertj.core.data.Offset<Double> offset(double tol) {
            return org.assertj.core.data.Offset.offset(tol);
        }
    }
}
