package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InferentialDistanceTest {

    @Test
    void identicalDistributionsZero() {
        double[] p = {0.5, 0.3, 0.2};
        assertThat(InferentialDistance.klDivergence(p, p)).isEqualTo(0.0);
        assertThat(InferentialDistance.jsDivergence(p, p)).isEqualTo(0.0);
        assertThat(InferentialDistance.hellingerDistance(p, p)).isEqualTo(0.0);
        assertThat(InferentialDistance.totalVariationDistance(p, p)).isEqualTo(0.0);
    }

    @Test
    void uniformDistributionsKLIsLogN() {
        double[] p = {0.25, 0.25, 0.25, 0.25};
        double[] q = {0.25, 0.25, 0.25, 0.25};
        assertThat(InferentialDistance.klDivergence(p, q)).isEqualTo(0.0);
    }

    @Test
    void highlyDifferentDistributionsHaveHighDivergence() {
        double[] p = {1.0, 0.0, 0.0};
        double[] q = {0.0, 1.0, 0.0};
        // KL: P || Q = infinity (P[0]=1 but Q[0]=0)
        assertThat(InferentialDistance.klDivergence(p, q)).isEqualTo(Double.POSITIVE_INFINITY);
        // JS: bounded [0, 1]
        double js = InferentialDistance.jsDivergence(p, q);
        assertThat(js).isBetween(0.0, 1.0);
        // Hellinger: bounded [0, 1]
        double h = InferentialDistance.hellingerDistance(p, q);
        assertThat(h).isBetween(0.0, 1.0);
        // TV: should be 1.0 (fully disjoint)
        assertThat(InferentialDistance.totalVariationDistance(p, q)).isEqualTo(1.0);
    }

    @Test
    void jensenShannonIsSymmetric() {
        double[] p = {0.5, 0.3, 0.2};
        double[] q = {0.1, 0.6, 0.3};
        double js1 = InferentialDistance.jsDivergence(p, q);
        double js2 = InferentialDistance.jsDivergence(q, p);
        assertThat(js1).isCloseTo(js2, within(1e-9));
    }

    @Test
    void hellingerIsSymmetric() {
        double[] p = {0.5, 0.3, 0.2};
        double[] q = {0.1, 0.6, 0.3};
        double h1 = InferentialDistance.hellingerDistance(p, q);
        double h2 = InferentialDistance.hellingerDistance(q, p);
        assertThat(h1).isCloseTo(h2, within(1e-9));
    }

    @Test
    void tvIsSymmetric() {
        double[] p = {0.5, 0.3, 0.2};
        double[] q = {0.1, 0.6, 0.3};
        double tv1 = InferentialDistance.totalVariationDistance(p, q);
        double tv2 = InferentialDistance.totalVariationDistance(q, p);
        assertThat(tv1).isCloseTo(tv2, within(1e-9));
    }

    @Test
    void compositeBoundedZeroOne() {
        double[] p = {0.5, 0.3, 0.2};
        double[] q = {0.4, 0.4, 0.2};
        double c = InferentialDistance.compositeDistance(p, q);
        assertThat(c).isBetween(0.0, 1.0);
    }

    @Test
    void mismatchedLengthsThrow() {
        assertThatThrownBy(() -> InferentialDistance.klDivergence(new double[]{0.5}, new double[]{0.3, 0.7}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
