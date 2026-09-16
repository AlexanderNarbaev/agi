package io.matrix.consciousness;

import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W193 — EntropyDecomposition property-based tests.
 */
class EntropyDecompositionPropertyTest {

    @Property(tries = 50)
    void propertyEntropyNonNegative(@ForAll("distributions") double[] p) {
        double h = EntropyDecomposition.entropy(p);
        if (!Double.isNaN(h)) {
            assertThat(h).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Property(tries = 50)
    void propertyEntropyBoundedByLogN(@ForAll("distributions") double[] p) {
        double h = EntropyDecomposition.entropy(p);
        double max = Math.log(p.length) / Math.log(2);
        assertThat(h).isLessThanOrEqualTo(max + 1e-9);
    }

    @Property(tries = 30)
    void propertyEntropyOfUniformIsLogN(@ForAll("uniformSizes") int n) {
        if (n < 2) return;
        double[] uniform = new double[n];
        for (int i = 0; i < n; i++) uniform[i] = 1.0;
        double h = EntropyDecomposition.entropy(uniform);
        assertThat(h).isCloseTo(Math.log(n) / Math.log(2), Assertions.offset(1e-9));
    }

    @Property(tries = 30)
    void propertyEntropyOfDeltaIsZero(@ForAll("uniformSizes") int n) {
        if (n < 1) return;
        double[] delta = new double[n];
        delta[0] = 1.0;
        double h = EntropyDecomposition.entropy(delta);
        assertThat(h).isCloseTo(0.0, Assertions.offset(1e-9));
    }

    @Property(tries = 30)
    void propertyJointEntropyBounded(@ForAll("jointMatrices") double[][] joint) {
        double h = EntropyDecomposition.jointEntropy(joint);
        if (!Double.isNaN(h) && joint.length > 0 && joint[0].length > 0) {
            double max = Math.log(joint.length * joint[0].length) / Math.log(2);
            assertThat(h).isLessThanOrEqualTo(max + 1e-9);
        }
    }

    @Property(tries = 30)
    void propertyMutualInformationNonNegative(@ForAll("independentPairs") double[][] pair) {
        if (pair.length < 2 || pair[0].length < 2) return;
        double[][] joint = new double[pair[0].length][pair[1].length];
        for (int i = 0; i < pair[0].length; i++) {
            for (int j = 0; j < pair[1].length; j++) {
                joint[i][j] = pair[0][i] * pair[1][j];
            }
        }
        double mi = EntropyDecomposition.mutualInformation(pair[0], pair[1], joint);
        if (!Double.isNaN(mi)) {
            assertThat(mi).isCloseTo(0.0, Assertions.offset(0.01));
        }
    }

    @Property(tries = 30)
    void propertyDecomposeConsistency(@ForAll("jointMatrices") double[][] data) {
        if (data.length < 2 || data[0].length < 2) return;
        double[] p = EntropyDecomposition.marginalX(data);
        double[] q = EntropyDecomposition.marginalY(data);
        if (p.length == 0 || q.length == 0) return;
        EntropyDecomposition.EntropyComponents comp = EntropyDecomposition.decompose(p, q, data);
        assertThat(comp.hXY()).isCloseTo(comp.hX() + comp.hY() - comp.mutualInformation(), Assertions.offset(1e-9));
    }

    @Provide
    Arbitrary<double[]> distributions() {
        return Arbitraries.integers().between(2, 8).flatMap(n ->
            Arbitraries.doubles().between(0.01, 1.0).array(double[].class).ofSize(n));
    }

    @Provide
    Arbitrary<int[]> uniformSizes() {
        return Arbitraries.integers().between(2, 8).array(int[].class).ofSize(1);
    }

    @Provide
    Arbitrary<double[][]> jointMatrices() {
        return Arbitraries.integers().between(2, 4).flatMap(rows ->
            Arbitraries.integers().between(2, 4).flatMap(cols ->
                Arbitraries.doubles().between(0.01, 1.0).array(double[].class).array(double[][].class).ofSize(cols).map(arr -> {
                    double[][] result = new double[rows][cols];
                    for (int i = 0; i < rows; i++) {
                        for (int j = 0; j < cols; j++) {
                            result[i][j] = arr[Math.min(i, arr.length - 1)][Math.min(j, arr[0].length - 1)];
                        }
                    }
                    return result;
                })));
    }

    @Provide
    Arbitrary<double[][]> independentPairs() {
        return Arbitraries.integers().between(2, 4).flatMap(rows ->
            Arbitraries.integers().between(2, 4).flatMap(cols ->
                Arbitraries.doubles().between(0.01, 1.0).array(double[].class).ofSize(rows).flatMap(p1 ->
                    Arbitraries.doubles().between(0.01, 1.0).array(double[].class).ofSize(cols)
                        .map(p2 -> new double[][]{p1, p2}))));
    }

    private static class Assertions {
        static org.assertj.core.data.Offset<Double> offset(double tol) {
            return org.assertj.core.data.Offset.offset(tol);
        }
    }
}
