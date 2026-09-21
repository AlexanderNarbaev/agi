package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MultivariateGaussianAnalyzerTest {

    @Test
    void meanOfConstantIsConstant() {
        double[][] data = {{1.0, 2.0}, {1.0, 2.0}, {1.0, 2.0}};
        double[] m = MultivariateGaussianAnalyzer.mean(data);
        assertThat(m[0]).isEqualTo(1.0);
        assertThat(m[1]).isEqualTo(2.0);
    }

    @Test
    void covarianceOfConstantIsZero() {
        double[][] data = {{5.0, 5.0}, {5.0, 5.0}, {5.0, 5.0}};
        double[][] cov = MultivariateGaussianAnalyzer.covariance(data);
        assertThat(cov[0][0]).isCloseTo(0.0, within(1e-9));
        assertThat(cov[1][1]).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void covarianceSymmetric() {
        double[][] data = {{1.0, 2.0}, {3.0, 4.0}, {5.0, 6.0}};
        double[][] cov = MultivariateGaussianAnalyzer.covariance(data);
        assertThat(cov[0][1]).isCloseTo(cov[1][0], within(1e-9));
    }

    @Test
    void correlationBounded() {
        double[][] data = {{1.0, 2.0}, {3.0, 4.0}, {5.0, 6.0}, {7.0, 8.0}};
        double[][] corr = MultivariateGaussianAnalyzer.correlation(data);
        assertThat(corr[0][0]).isCloseTo(1.0, within(1e-9));
        assertThat(corr[1][1]).isCloseTo(1.0, within(1e-9));
        assertThat(corr[0][1]).isBetween(-1.0, 1.0);
    }

    @Test
    void logDeterminantOfIdentityIsZero() {
        double[][] identity = {{1.0, 0.0}, {0.0, 1.0}};
        double ld = MultivariateGaussianAnalyzer.logDeterminant(identity);
        assertThat(ld).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void logDeterminantNegativeInfinityForSingular() {
        double[][] singular = {{1.0, 1.0}, {1.0, 1.0}};
        double ld = MultivariateGaussianAnalyzer.logDeterminant(singular);
        assertThat(ld).isEqualTo(Double.NEGATIVE_INFINITY);
    }

    @Test
    void mahalanobisDistanceZeroForMean() {
        double[] mu = {0.0, 0.0};
        double[][] identity = {{1.0, 0.0}, {0.0, 1.0}};
        double d = MultivariateGaussianAnalyzer.mahalanobisDistance(mu, mu, identity);
        assertThat(d).isEqualTo(0.0);
    }

    @Test
    void emptyDataReturnsEmpty() {
        assertThat(MultivariateGaussianAnalyzer.mean(new double[0][])).isEmpty();
        assertThat(MultivariateGaussianAnalyzer.covariance(new double[0][])).isEmpty();
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
