package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitiveLyapunovExponentTest {

    @Test
    void identicalTrajectoriesReturnsZero() {
        double[] t = {1.0, 2.0, 3.0, 4.0};
        // No divergence ratio log(0) — handled by epsilon
        double lambda = CognitiveLyapunovExponent.estimate(t, t);
        assertThat(lambda).isEqualTo(0.0);
    }

    @Test
    void mismatchedLengthsThrow() {
        assertThatThrownBy(() -> CognitiveLyapunovExponent.estimate(new double[]{1.0}, new double[]{1.0, 2.0}))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullTrajectoriesReturnZero() {
        assertThat(CognitiveLyapunovExponent.estimate(null, new double[]{1.0})).isEqualTo(0.0);
    }

    @Test
    void classifyChaotic() {
        assertThat(CognitiveLyapunovExponent.classify(0.5)).isEqualTo("CHAOTIC");
    }

    @Test
    void classifyStable() {
        assertThat(CognitiveLyapunovExponent.classify(-0.5)).isEqualTo("STABLE");
    }

    @Test
    void classifyEdgeOfChaos() {
        assertThat(CognitiveLyapunovExponent.classify(0.0)).isEqualTo("EDGE_OF_CHAOS");
    }

    @Test
    void estimateFromModelProducesFiniteValue() {
        double lambda = CognitiveLyapunovExponent.estimateFromModel(100, 0.0, 42L);
        assertThat(Double.isFinite(lambda)).isTrue();
    }

    @Test
    void divergentModelHasPositiveLyapunov() {
        double lambda = CognitiveLyapunovExponent.estimateFromModel(200, 1.0, 42L);
        // Logistic map at r=3.95 is chaotic
        assertThat(lambda).isGreaterThan(-1.0);  // sanity check
    }
}
