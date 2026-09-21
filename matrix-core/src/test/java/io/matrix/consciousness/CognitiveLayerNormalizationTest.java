package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveLayerNormalizationTest {

    @Test
    void nullReturnsNull() {
        assertThat(CognitiveLayerNormalization.apply(null)).isNull();
    }

    @Test
    void emptyReturnsEmpty() {
        double[] result = CognitiveLayerNormalization.apply(new double[0]);
        assertThat(result.length).isEqualTo(0);
    }

    @Test
    void preservesDimensions() {
        double[] v = {1, 2, 3, 4, 5};
        double[] result = CognitiveLayerNormalization.apply(v);
        assertThat(result.length).isEqualTo(5);
    }

    @Test
    void zeroMeanAfterNormalization() {
        double[] v = {1, 2, 3, 4, 5};
        double[] result = CognitiveLayerNormalization.apply(v);
        double sum = 0;
        for (double x : result) sum += x;
        assertThat(sum / v.length).isCloseTo(0.0, offset(1e-9));
    }

    @Test
    void unitVarianceAfterNormalization() {
        double[] v = {1, 2, 3, 4, 5};
        double[] result = CognitiveLayerNormalization.apply(v);
        double mean = 0;
        for (double x : result) mean += x;
        mean /= v.length;
        double var = 0;
        for (double x : result) {
            double d = x - mean;
            var += d * d;
        }
        var /= v.length;
        assertThat(var).isCloseTo(1.0, offset(1e-2));
    }

    @Test
    void withGammaAndBeta() {
        double[] v = {1, 2, 3, 4};
        double[] gamma = {2, 2, 2, 2};
        double[] beta = {1, 1, 1, 1};
        double[] result = CognitiveLayerNormalization.apply(v, gamma, beta);
        assertThat(result.length).isEqualTo(4);
    }

    @Test
    void statsReturnsMeanAndVar() {
        double[] v = {1, 2, 3, 4, 5};
        double[] stats = CognitiveLayerNormalization.stats(v);
        assertThat(stats.length).isEqualTo(2);
        assertThat(stats[0]).isEqualTo(3.0); // mean
        assertThat(stats[1]).isEqualTo(2.0); // variance
    }

    @Test
    void rmsNormPreservesShape() {
        double[] v = {1, 2, 3, 4};
        double[] result = CognitiveLayerNormalization.rmsNorm(v, null);
        assertThat(result.length).isEqualTo(4);
    }

    @Test
    void rmsNormWithGamma() {
        double[] v = {1, 2, 3, 4};
        double[] gamma = {2, 2, 2, 2};
        double[] result = CognitiveLayerNormalization.rmsNorm(v, gamma);
        assertThat(result.length).isEqualTo(4);
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
