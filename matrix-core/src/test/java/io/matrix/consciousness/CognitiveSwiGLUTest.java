package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveSwiGLUTest {

    @Test
    void constructValid() {
        CognitiveSwiGLU glu = new CognitiveSwiGLU(64, 128, 42L);
        assertThat(glu.dim()).isEqualTo(64);
        assertThat(glu.hiddenDim()).isEqualTo(128);
        assertThat(glu.seed()).isEqualTo(42L);
    }

    @Test
    void invalidDimsThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveSwiGLU(0, 128, 1L)
        );
    }

    @Test
    void applyPreservesDimensions() {
        CognitiveSwiGLU glu = new CognitiveSwiGLU(64, 128, 42L);
        double[] v = new double[64];
        for (int i = 0; i < 64; i++) v[i] = i / 64.0;
        double[] result = glu.apply(v);
        assertThat(result.length).isEqualTo(64);
    }

    @Test
    void nullApplyReturnsNull() {
        CognitiveSwiGLU glu = new CognitiveSwiGLU(64, 128, 42L);
        assertThat(glu.apply(null)).isNull();
    }

    @Test
    void wrongDimReturnsInput() {
        CognitiveSwiGLU glu = new CognitiveSwiGLU(64, 128, 42L);
        double[] v = new double[32];
        assertThat(glu.apply(v)).isSameAs(v);
    }

    @Test
    void applyToProfileReturnsValid() {
        CognitiveSwiGLU glu = new CognitiveSwiGLU(64, 128, 42L);
        CognitiveGenesisProfile p = new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
        double[] result = glu.applyToProfile(p);
        assertThat(result).isNotNull();
        assertThat(result.length).isEqualTo(64);
    }

    @Test
    void parameterCountCorrect() {
        CognitiveSwiGLU glu = new CognitiveSwiGLU(64, 128, 1L);
        assertThat(glu.parameterCount()).isEqualTo(64L * 128 * 3);
    }

    @Test
    void sameSeedDeterministic() {
        CognitiveSwiGLU g1 = new CognitiveSwiGLU(64, 128, 42L);
        CognitiveSwiGLU g2 = new CognitiveSwiGLU(64, 128, 42L);
        double[] v = new double[64];
        for (int i = 0; i < 64; i++) v[i] = i / 64.0;
        double[] r1 = g1.apply(v);
        double[] r2 = g2.apply(v);
        for (int i = 0; i < 64; i++) {
            assertThat(r1[i]).isCloseTo(r2[i], offset(1e-9));
        }
    }

    @Test
    void nullProfileReturnsNull() {
        CognitiveSwiGLU glu = new CognitiveSwiGLU(64, 128, 42L);
        assertThat(glu.applyToProfile(null)).isNull();
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
