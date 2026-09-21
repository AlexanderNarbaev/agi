package io.matrix.consciousness;

import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W165 — MultivariateGaussianAnalyzer property-based tests.
 */
class MultivariateGaussianAnalyzerPropertyTest {

    @Property(tries = 50)
    void propertyMeanOfConstantIsConstant(@ForAll("values") double v,
                                           @ForAll("lengths") int n,
                                           @ForAll("dims") int d) {
        if (n < 1 || d < 1) return;
        double[][] data = new double[n][d];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) data[i][j] = v;
        }
        double[] m = MultivariateGaussianAnalyzer.mean(data);
        for (double mi : m) {
            assertThat(mi).isCloseTo(v, Assertions.offset(1e-9));
        }
    }

    @Property(tries = 30)
    void propertyCovarianceSymmetric(@ForAll("sampleMatrices") double[][] data) {
        if (data.length < 2 || data[0].length < 1) return;
        double[][] cov = MultivariateGaussianAnalyzer.covariance(data);
        int d = cov.length;
        for (int j = 0; j < d; j++) {
            for (int k = 0; k < d; k++) {
                assertThat(cov[j][k]).isCloseTo(cov[k][j], Assertions.offset(1e-9));
            }
        }
    }

    @Property(tries = 30)
    void propertyCorrelationDiagonalIsOne(@ForAll("sampleMatrices") double[][] data) {
        if (data.length < 2 || data[0].length < 1) return;
        double[][] corr = MultivariateGaussianAnalyzer.correlation(data);
        for (int j = 0; j < corr.length; j++) {
            assertThat(corr[j][j]).isCloseTo(1.0, Assertions.offset(1e-9));
        }
    }

    @Property(tries = 30)
    void propertyCorrelationBounded(@ForAll("sampleMatrices") double[][] data) {
        if (data.length < 2 || data[0].length < 1) return;
        double[][] corr = MultivariateGaussianAnalyzer.correlation(data);
        for (int j = 0; j < corr.length; j++) {
            for (int k = 0; k < corr.length; k++) {
                assertThat(corr[j][k]).isBetween(-1.0, 1.0);
            }
        }
    }

    @Property(tries = 30)
    void propertyMahalanobisZeroAtMean(@ForAll("sampleMatrices") double[][] data) {
        if (data.length < 2 || data[0].length < 1) return;
        int d = data[0].length;
        double[] mu = MultivariateGaussianAnalyzer.mean(data);
        // Identity inverse covariance
        double[][] identity = new double[d][d];
        for (int i = 0; i < d; i++) identity[i][i] = 1.0;
        double distance = MultivariateGaussianAnalyzer.mahalanobisDistance(mu, mu, identity);
        assertThat(distance).isCloseTo(0.0, Assertions.offset(1e-9));
    }

    @Provide
    Arbitrary<Double> values() {
        return Arbitraries.doubles().between(-100.0, 100.0);
    }

    @Provide
    Arbitrary<Integer> lengths() {
        return Arbitraries.integers().between(1, 16);
    }

    @Provide
    Arbitrary<Integer> dims() {
        return Arbitraries.integers().between(1, 4);
    }

    @Provide
    Arbitrary<double[][]> sampleMatrices() {
        return Arbitraries.integers().between(2, 16).flatMap(n ->
            Arbitraries.integers().between(1, 4).flatMap(d ->
                Arbitraries.doubles().between(-10.0, 10.0).array(double[].class).ofSize(d)
                    .array(double[][].class).ofSize(n)));
    }

    private static class Assertions {
        static org.assertj.core.data.Offset<Double> offset(double tol) {
            return org.assertj.core.data.Offset.offset(tol);
        }
    }
}
