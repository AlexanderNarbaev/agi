package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveResidualConnectionTest {

    @Test
    void residualAdds() {
        double[] x = {1.0, 2.0, 3.0};
        double[] sub = {0.5, 0.5, 0.5};
        double[] result = CognitiveResidualConnection.residual(x, sub);
        assertThat(result[0]).isEqualTo(1.5);
        assertThat(result[1]).isEqualTo(2.5);
        assertThat(result[2]).isEqualTo(3.5);
    }

    @Test
    void nullXReturnsSublayer() {
        double[] sub = {1.0, 2.0};
        assertThat(CognitiveResidualConnection.residual(null, sub)).isSameAs(sub);
    }

    @Test
    void nullSublayerReturnsX() {
        double[] x = {1.0, 2.0};
        assertThat(CognitiveResidualConnection.residual(x, null)).isSameAs(x);
    }

    @Test
    void differentLengthReturnsX() {
        double[] x = {1.0, 2.0};
        double[] sub = {1.0, 2.0, 3.0};
        assertThat(CognitiveResidualConnection.residual(x, sub)).isSameAs(x);
    }

    @Test
    void scaledResidualScales() {
        double[] x = {1.0, 2.0};
        double[] sub = {1.0, 1.0};
        double[] result = CognitiveResidualConnection.scaledResidual(x, sub, 2.0);
        assertThat(result[0]).isEqualTo(3.0);
        assertThat(result[1]).isEqualTo(4.0);
    }

    @Test
    void gatedResidualClampsGate() {
        double[] x = {1.0, 2.0};
        double[] sub = {3.0, 4.0};
        double[] result = CognitiveResidualConnection.gatedResidual(x, sub, 0.5);
        assertThat(result[0]).isEqualTo(2.5);
        assertThat(result[1]).isEqualTo(4.0);
    }

    @Test
    void highwayBounded() {
        double[] x = {2.0, 4.0};
        double[] sub = {0.0, 0.0};
        double[] result = CognitiveResidualConnection.highway(x, sub, 1.0);
        // gate=1 → output = x
        assertThat(result[0]).isEqualTo(2.0);
        double[] result0 = CognitiveResidualConnection.highway(x, sub, 0.0);
        // gate=0 → output = sub
        assertThat(result0[0]).isEqualTo(0.0);
    }

    @Test
    void chainAppliesAll() {
        double[] x = {1.0};
        double[][] chain = {{0.5}, {0.5}, {0.5}};
        double[] result = CognitiveResidualConnection.chain(x, chain);
        assertThat(result[0]).isEqualTo(2.5);
    }

    @Test
    void chainEmptyReturnsX() {
        double[] x = {1.0};
        double[] result = CognitiveResidualConnection.chain(x, new double[0][]);
        assertThat(result).isSameAs(x);
    }
}
