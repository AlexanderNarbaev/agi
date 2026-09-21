package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveDistillationTest {

    @Test
    void constructValid() {
        CognitiveDistillation d = new CognitiveDistillation(128, 32, 42L);
        assertThat(d.teacherDim()).isEqualTo(128);
        assertThat(d.studentDim()).isEqualTo(32);
        assertThat(d.seed()).isEqualTo(42L);
    }

    @Test
    void invalidDimsThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveDistillation(0, 32, 1L)
        );
    }

    @Test
    void distillReturnsCorrectSize() {
        CognitiveDistillation d = new CognitiveDistillation(64, 16, 42L);
        double[] v = new double[64];
        for (int i = 0; i < 64; i++) v[i] = i / 64.0;
        double[] result = d.distill(v);
        assertThat(result.length).isEqualTo(16);
    }

    @Test
    void nullDistillReturnsNull() {
        CognitiveDistillation d = new CognitiveDistillation(64, 16, 42L);
        assertThat(d.distill(null)).isNull();
    }

    @Test
    void wrongDimReturnsNull() {
        CognitiveDistillation d = new CognitiveDistillation(64, 16, 42L);
        double[] v = new double[32];
        assertThat(d.distill(v)).isNull();
    }

    @Test
    void distillProfileReturnsCorrectSize() {
        CognitiveDistillation d = new CognitiveDistillation(64, 16, 42L);
        CognitiveGenesisProfile p = new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
        double[] result = d.distillProfile(p);
        assertThat(result.length).isEqualTo(16);
    }

    @Test
    void compressionRatioCorrect() {
        CognitiveDistillation d = new CognitiveDistillation(64, 16, 42L);
        assertThat(d.compressionRatio()).isEqualTo(4.0);
    }

    @Test
    void parameterCountCorrect() {
        CognitiveDistillation d = new CognitiveDistillation(64, 16, 1L);
        assertThat(d.parameterCount()).isEqualTo(64L * 16);
    }

    @Test
    void sameSeedDeterministic() {
        CognitiveDistillation d1 = new CognitiveDistillation(64, 16, 42L);
        CognitiveDistillation d2 = new CognitiveDistillation(64, 16, 42L);
        double[] v = new double[64];
        for (int i = 0; i < 64; i++) v[i] = i / 64.0;
        double[] r1 = d1.distill(v);
        double[] r2 = d2.distill(v);
        for (int i = 0; i < 16; i++) {
            assertThat(r1[i]).isCloseTo(r2[i], offset(1e-9));
        }
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
