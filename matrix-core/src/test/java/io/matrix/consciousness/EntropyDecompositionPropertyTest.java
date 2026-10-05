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

    /**
     * RECON-W32.29: was {@code Arbitraries.integers().between(2,8)
     * .array(int[].class).ofSize(1)}, an {@code Arbitrary<int[]>}, while both consumers
     * declare {@code @ForAll("uniformSizes") int n} — a scalar. jqwik cannot invoke the
     * method at all: it throws {@code argument type mismatch} at {@code Method.invoke}
     * before reaching a single assertion, so the test could never pass or fail on its
     * claim. A one-element array of integers is just an integer; nothing was gained.
     */
    @Provide
    Arbitrary<Integer> uniformSizes() {
        return Arbitraries.integers().between(2, 8);
    }

    @Provide
    Arbitrary<double[][]> jointMatrices() {
        return Arbitraries.integers().between(2, 4).flatMap(rows ->
            Arbitraries.integers().between(2, 4).flatMap(cols ->
                // RECON-W32.29: `.ofSize(cols)` constrains the INNER arbitrary, so the
                // OUTER array was unconstrained and jqwik could produce a length-0 one.
                // The clamping below then computed Math.min(0, -1) = -1 and indexed
                // arr[-1] — "Index -1 out of bounds for length 0" — a crash in the
                // generator, not a property being violated. The clamps were papering over
                // a shape the generator was not actually producing; fixing the shape is
                // the fix, and then the array is exactly rows x cols with no clamping
                // needed at all.
                // RECON-W32.29, second attempt. Constraining the OUTER array was not
                // enough: the INNER double[] was still unconstrained, so arr[i] could
                // itself have length 0 and arr[i].length - 1 became -1. Measured: the
                // failure was identical after the first fix, which is what said the
                // diagnosis was incomplete rather than the fix being wrong.
                // Both axes are now pinned, and NO clamping is needed: arr is exactly
                // rows x cols, so arr[i][j] is always in range.
                Arbitraries.doubles().between(0.01, 1.0)
                    .array(double[].class).ofMinSize(cols).ofMaxSize(cols)
                    .array(double[][].class).ofMinSize(rows).ofMaxSize(rows).map(arr -> {
                    double[][] result = new double[rows][cols];
                    for (int i = 0; i < rows; i++) {
                        for (int j = 0; j < cols; j++) {
                            result[i][j] = arr[i][j];
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
