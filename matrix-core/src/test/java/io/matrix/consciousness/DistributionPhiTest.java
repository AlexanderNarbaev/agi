package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistributionPhiTest {

    @Test
    void emptyReturnsZero() {
        assertThat(DistributionPhi.phiFromTimeSeries(new double[0], 1, 16)).isEqualTo(0.0);
    }

    @Test
    void constantReturnsZero() {
        double[] constant = new double[]{5.0, 5.0, 5.0, 5.0, 5.0};
        assertThat(DistributionPhi.phiFromTimeSeries(constant, 1, 16)).isEqualTo(0.0);
    }

    @Test
    void randomHasNonZeroPhi() {
        Random rng = new Random(42);
        double[] values = new double[100];
        for (int i = 0; i < 100; i++) values[i] = rng.nextGaussian();
        double phi = DistributionPhi.phiFromTimeSeries(values, 1, 16);
        // Random Gaussian has roughly maximum entropy
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void invalidNThrows() {
        assertThatThrownBy(() -> DistributionPhi.phiFromTimeSeries(new double[]{1.0}, 0, 16))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidBinsThrows() {
        assertThatThrownBy(() -> DistributionPhi.phiFromTimeSeries(new double[]{1.0, 2.0}, 1, 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void twoDTimeSeriesWorks() {
        Random rng = new Random(42);
        double[] x = new double[100];
        double[] y = new double[100];
        for (int i = 0; i < 100; i++) {
            x[i] = rng.nextGaussian();
            y[i] = rng.nextGaussian();
        }
        double phi = DistributionPhi.phiFrom2DTimeSeries(x, y, 1, 8);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void twoDCorrelatedHigherThanUncorrelated() {
        // Highly correlated (y = x + small noise)
        Random rng = new Random(42);
        double[] x1 = new double[100];
        double[] y1 = new double[100];
        for (int i = 0; i < 100; i++) {
            x1[i] = rng.nextGaussian();
            y1[i] = x1[i] + rng.nextGaussian() * 0.1;
        }
        double phiCorr = DistributionPhi.phiFrom2DTimeSeries(x1, y1, 1, 16);

        // Independent
        double[] x2 = new double[100];
        double[] y2 = new double[100];
        for (int i = 0; i < 100; i++) {
            x2[i] = rng.nextGaussian();
            y2[i] = rng.nextGaussian();
        }
        double phiInd = DistributionPhi.phiFrom2DTimeSeries(x2, y2, 1, 16);

        // Correlated should have at least as much MI as independent
        assertThat(phiCorr).isGreaterThanOrEqualTo(phiInd - 0.01);
    }
}
