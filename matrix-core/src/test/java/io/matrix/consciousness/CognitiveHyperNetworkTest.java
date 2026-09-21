package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveHyperNetworkTest {

    @Test
    void constructValid() {
        CognitiveHyperNetwork hn = new CognitiveHyperNetwork(32, 16, 8, 42L);
        assertThat(hn.inputDim()).isEqualTo(32);
        assertThat(hn.hiddenDim()).isEqualTo(16);
        assertThat(hn.outputDim()).isEqualTo(8);
    }

    @Test
    void invalidDimsThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveHyperNetwork(0, 16, 8, 1L)
        );
    }

    @Test
    void generateReturnsCorrectShape() {
        CognitiveHyperNetwork hn = new CognitiveHyperNetwork(32, 16, 8, 42L);
        double[] input = new double[32];
        for (int i = 0; i < 32; i++) input[i] = i / 32.0;
        CognitiveHyperNetwork.GeneratedWeights gw = hn.generate(input);
        assertThat(gw.weights().length).isEqualTo(8);
        assertThat(gw.weights()[0].length).isEqualTo(16);
    }

    @Test
    void generateNullReturnsEmpty() {
        CognitiveHyperNetwork hn = new CognitiveHyperNetwork(32, 16, 8, 42L);
        CognitiveHyperNetwork.GeneratedWeights gw = hn.generate(null);
        assertThat(gw.weights().length).isEqualTo(0);
    }

    @Test
    void generateWrongDimReturnsEmpty() {
        CognitiveHyperNetwork hn = new CognitiveHyperNetwork(32, 16, 8, 42L);
        CognitiveHyperNetwork.GeneratedWeights gw = hn.generate(new double[16]);
        assertThat(gw.weights().length).isEqualTo(0);
    }

    @Test
    void applyReturnsCorrectSize() {
        CognitiveHyperNetwork hn = new CognitiveHyperNetwork(32, 16, 8, 42L);
        double[] input = new double[32];
        CognitiveHyperNetwork.GeneratedWeights gw = hn.generate(input);
        double[] result = hn.apply(input, gw);
        assertThat(result.length).isEqualTo(16);
    }

    @Test
    void parameterCountCorrect() {
        CognitiveHyperNetwork hn = new CognitiveHyperNetwork(32, 16, 8, 42L);
        assertThat(hn.parameterCount()).isEqualTo(32L * 8 * 16);
    }

    @Test
    void sameSeedDeterministic() {
        CognitiveHyperNetwork h1 = new CognitiveHyperNetwork(32, 16, 8, 42L);
        CognitiveHyperNetwork h2 = new CognitiveHyperNetwork(32, 16, 8, 42L);
        double[] input = new double[32];
        CognitiveHyperNetwork.GeneratedWeights gw1 = h1.generate(input);
        CognitiveHyperNetwork.GeneratedWeights gw2 = h2.generate(input);
        for (int i = 0; i < gw1.weights().length; i++) {
            for (int j = 0; j < gw1.weights()[0].length; j++) {
                assertThat(gw1.weights()[i][j]).isCloseTo(gw2.weights()[i][j], offset(1e-9));
            }
        }
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
