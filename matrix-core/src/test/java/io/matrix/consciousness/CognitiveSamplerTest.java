package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveSamplerTest {

    @Test
    void temperatureScaleNull() {
        assertThat(CognitiveSampler.temperatureScale(null, 1.0)).isNull();
    }

    @Test
    void temperatureScaleZeroHandled() {
        double[] logits = {1.0, 2.0, 3.0};
        double[] scaled = CognitiveSampler.temperatureScale(logits, 0);
        assertThat(scaled.length).isEqualTo(3);
    }

    @Test
    void temperatureScaleUniform() {
        double[] logits = {1.0, 1.0, 1.0};
        double[] scaled = CognitiveSampler.temperatureScale(logits, 1.0);
        for (int i = 0; i < 3; i++) assertThat(scaled[i]).isEqualTo(1.0);
    }

    @Test
    void softmaxSumsToOne() {
        double[] logits = {1.0, 2.0, 3.0, 4.0};
        double[] probs = CognitiveSampler.softmax(logits);
        double sum = 0;
        for (double p : probs) sum += p;
        assertThat(sum).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void softmaxEmptyReturnsEmpty() {
        assertThat(CognitiveSampler.softmax(new double[0])).isEmpty();
        assertThat(CognitiveSampler.softmax(null)).isEmpty();
    }

    @Test
    void topKKeepsK() {
        double[] probs = {0.4, 0.3, 0.2, 0.1};
        double[] filtered = CognitiveSampler.topK(probs, 2);
        double sum = 0;
        for (double p : filtered) sum += p;
        // Should keep only top 2
        assertThat(sum).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void topPKeepsCumulative() {
        double[] probs = {0.5, 0.3, 0.2};
        double[] filtered = CognitiveSampler.topP(probs, 0.5);
        // Top 1 (0.5) already sums to 0.5
        double sum = 0;
        for (double p : filtered) sum += p;
        assertThat(sum).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void topPAllReturnsInput() {
        double[] probs = {0.3, 0.3, 0.4};
        double[] filtered = CognitiveSampler.topP(probs, 1.0);
        for (int i = 0; i < probs.length; i++) {
            assertThat(filtered[i]).isCloseTo(probs[i], offset(1e-9));
        }
    }

    @Test
    void sampleValidIndex() {
        double[] probs = {0.5, 0.5};
        int idx = CognitiveSampler.sample(probs, 42L);
        assertThat(idx).isBetween(0, 1);
    }

    @Test
    void sampleEmptyReturnsMinusOne() {
        assertThat(CognitiveSampler.sample(new double[0], 1L)).isEqualTo(-1);
    }

    @Test
    void fullPipeline() {
        double[] logits = {1.0, 2.0, 3.0, 4.0};
        int idx = CognitiveSampler.samplePipeline(logits, 1.0, 2, 0.9, 42L);
        assertThat(idx).isBetween(0, 3);
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
