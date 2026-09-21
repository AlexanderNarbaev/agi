package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeriesCorrelatorTest {

    @Test
    void pearsonIdenticalIsOne() {
        double[] a = {1.0, 2.0, 3.0, 4.0, 5.0};
        assertThat(SeriesCorrelator.pearson(a, a)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void pearsonInverseIsNegativeOne() {
        double[] a = {1.0, 2.0, 3.0, 4.0, 5.0};
        double[] b = {5.0, 4.0, 3.0, 2.0, 1.0};
        assertThat(SeriesCorrelator.pearson(a, b)).isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void pearsonConstantReturnsZero() {
        double[] a = {5.0, 5.0, 5.0, 5.0};
        double[] b = {1.0, 2.0, 3.0, 4.0};
        // Constant series has zero variance → returns 0
        assertThat(SeriesCorrelator.pearson(a, b)).isEqualTo(0.0);
    }

    @Test
    void spearmanIsOneForSameRank() {
        double[] a = {1.0, 5.0, 3.0, 7.0};
        double[] b = {10.0, 50.0, 30.0, 70.0};  // same rank
        assertThat(SeriesCorrelator.spearman(a, b)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void crossCorrelationZeroLagIsPearson() {
        double[] a = {1.0, 2.0, 3.0, 4.0, 5.0};
        double[] b = {2.0, 4.0, 6.0, 8.0, 10.0};
        double cc = SeriesCorrelator.crossCorrelation(a, b, 0);
        double p = SeriesCorrelator.pearson(a, b);
        assertThat(cc).isCloseTo(p, within(1e-9));
    }

    @Test
    void autocorrelationAtZeroIsOne() {
        double[] x = {1.0, 3.0, 2.0, 5.0, 4.0};
        double[] acf = SeriesCorrelator.autocorrelation(x, 4);
        assertThat(acf[0]).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void autocorrelationLengthMatches() {
        double[] x = {1.0, 2.0, 3.0, 4.0, 5.0};
        double[] acf = SeriesCorrelator.autocorrelation(x, 3);
        assertThat(acf.length).isEqualTo(4);
    }

    @Test
    void mismatchedLengthsThrow() {
        assertThatThrownBy(() -> SeriesCorrelator.pearson(new double[]{1.0, 2.0}, new double[]{1.0}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
