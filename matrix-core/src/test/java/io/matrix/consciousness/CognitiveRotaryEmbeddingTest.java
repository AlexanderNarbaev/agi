package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveRotaryEmbeddingTest {

    @Test
    void nullReturnsNull() {
        assertThat(CognitiveRotaryEmbedding.apply(null, 0)).isNull();
        assertThat(CognitiveRotaryEmbedding.inverse(null, 0)).isNull();
    }

    @Test
    void preserveDimensions() {
        double[] v = {1.0, 2.0, 3.0, 4.0};
        double[] rotated = CognitiveRotaryEmbedding.apply(v, 0);
        assertThat(rotated.length).isEqualTo(4);
    }

    @Test
    void positionZeroReturnsOriginal() {
        double[] v = {1.0, 2.0, 3.0, 4.0};
        double[] rotated = CognitiveRotaryEmbedding.apply(v, 0);
        assertThat(rotated[0]).isCloseTo(1.0, offset(1e-9));
        assertThat(rotated[1]).isCloseTo(2.0, offset(1e-9));
        assertThat(rotated[2]).isCloseTo(3.0, offset(1e-9));
        assertThat(rotated[3]).isCloseTo(4.0, offset(1e-9));
    }

    @Test
    void inverseUndoesRotation() {
        double[] v = {1.0, 2.0, 3.0, 4.0};
        double[] rotated = CognitiveRotaryEmbedding.apply(v, 5);
        double[] back = CognitiveRotaryEmbedding.inverse(rotated, 5);
        for (int i = 0; i < v.length; i++) {
            assertThat(back[i]).isCloseTo(v[i], offset(1e-9));
        }
    }

    @Test
    void preservesL2Norm() {
        double[] v = {1.0, 2.0, 3.0, 4.0};
        double normBefore = Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2] + v[3]*v[3]);
        double[] rotated = CognitiveRotaryEmbedding.apply(v, 10);
        double normAfter = Math.sqrt(rotated[0]*rotated[0] + rotated[1]*rotated[1] +
                                       rotated[2]*rotated[2] + rotated[3]*rotated[3]);
        assertThat(normAfter).isCloseTo(normBefore, offset(1e-9));
    }

    @Test
    void frequencyComputation() {
        double f0 = CognitiveRotaryEmbedding.frequency(0, 4, 10000.0);
        assertThat(f0).isEqualTo(1.0);
        double f1 = CognitiveRotaryEmbedding.frequency(1, 4, 10000.0);
        assertThat(f1).isLessThan(1.0);
    }

    @Test
    void differentPositionsDifferentRotation() {
        double[] v = {1.0, 0.0, 0.0, 0.0};
        double[] r0 = CognitiveRotaryEmbedding.apply(v, 0);
        double[] r1 = CognitiveRotaryEmbedding.apply(v, 1);
        assertThat(r0[0]).isNotEqualTo(r1[0]);
    }

    @Test
    void customBase() {
        double[] v = {1.0, 0.0};
        double[] r1 = CognitiveRotaryEmbedding.apply(v, 1, 100.0);
        double[] r2 = CognitiveRotaryEmbedding.apply(v, 1, 10000.0);
        assertThat(r1[0]).isNotEqualTo(r2[0]);
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
