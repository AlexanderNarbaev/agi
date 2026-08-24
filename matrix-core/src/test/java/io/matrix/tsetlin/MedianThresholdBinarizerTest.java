package io.matrix.tsetlin;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MedianThresholdBinarizerTest {

    @Test
    void medianIsCapturedAndFrozen() {
        var b = new MedianThresholdBinarizer(2);
        assertThat(b.isFrozen()).isFalse();
        b.fit(new double[][]{{1, 10}, {2, 20}, {3, 30}, {4, 40}});
        assertThat(b.isFrozen()).isTrue();
        assertThat(b.threshold(0)).isEqualTo(2.5); // even-count median
        assertThat(b.threshold(1)).isEqualTo(25.0);
        // transform matches captured medians exactly
        assertThat(b.transform(new double[]{2.5, 24.9})[0]).isEqualTo(0b00); // f0 not >2.5, f1 not >25
        assertThat(b.transform(new double[]{3, 26})[0]).isEqualTo(0b11);
    }

    @Test
    void constantFeatureCollapsesToZeroBit() {
        var b = new MedianThresholdBinarizer(1);
        b.fit(new double[][]{{5}, {5}, {5}});
        assertThat(b.threshold(0)).isEqualTo(5.0);
        assertThat(Long.bitCount(b.transform(new double[]{5})[0])).isZero(); // strictly-greater fails on equal
    }

    @Test
    void unfittedTransformFailsFast() {
        var b = new MedianThresholdBinarizer(3);
        assertThatThrownBy(() -> b.transform(new double[]{1, 2, 3}))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> b.threshold(0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deterministicAcrossInstances() {
        Random r = new Random(7);
        double[][] data = new double[100][6];
        for (double[] row : data) for (int j = 0; j < 6; j++) row[j] = r.nextGaussian();
        var a = new MedianThresholdBinarizer(6); a.fit(data);
        var c = new MedianThresholdBinarizer(6); c.fit(data);
        for (int t = 0; t < 50; t++) {
            double[] v = new double[6];
            for (int j = 0; j < 6; j++) v[j] = r.nextGaussian();
            assertThat(a.transform(v)).isEqualTo(c.transform(v));
        }
    }
}
