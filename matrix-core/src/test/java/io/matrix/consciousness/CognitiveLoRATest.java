package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveLoRATest {

    @Test
    void constructValid() {
        CognitiveLoRA lora = new CognitiveLoRA(64, 8, 1.0, 42L);
        assertThat(lora.dim()).isEqualTo(64);
        assertThat(lora.rank()).isEqualTo(8);
        assertThat(lora.seed()).isEqualTo(42L);
    }

    @Test
    void invalidDimThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveLoRA(0, 8, 1.0, 1L)
        );
    }

    @Test
    void invalidRankThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveLoRA(64, 0, 1.0, 1L)
        );
    }

    @Test
    void rankLargerThanDimThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveLoRA(8, 16, 1.0, 1L)
        );
    }

    @Test
    void adaptPreservesDimensions() {
        CognitiveLoRA lora = new CognitiveLoRA(64, 8, 1.0, 42L);
        double[] v = new double[64];
        for (int i = 0; i < 64; i++) v[i] = i / 64.0;
        double[] adapted = lora.adapt(v);
        assertThat(adapted.length).isEqualTo(64);
    }

    @Test
    void adaptNullReturnsNull() {
        CognitiveLoRA lora = new CognitiveLoRA(64, 8, 1.0, 42L);
        assertThat(lora.adapt(null)).isNull();
    }

    @Test
    void adaptWrongDimensionReturnsInput() {
        CognitiveLoRA lora = new CognitiveLoRA(64, 8, 1.0, 42L);
        double[] v = new double[32];
        assertThat(lora.adapt(v)).isSameAs(v);
    }

    @Test
    void parameterCountCompressed() {
        CognitiveLoRA lora = new CognitiveLoRA(64, 4, 1.0, 1L);
        long params = lora.parameterCount();
        long full = lora.fullRankParameterCount();
        assertThat(params).isLessThan(full);
    }

    @Test
    void compressionRatioGreaterThanOne() {
        CognitiveLoRA lora = new CognitiveLoRA(64, 4, 1.0, 1L);
        assertThat(lora.compressionRatio()).isGreaterThan(1.0);
    }

    @Test
    void sameSeedDeterministic() {
        CognitiveLoRA l1 = new CognitiveLoRA(64, 8, 1.0, 42L);
        CognitiveLoRA l2 = new CognitiveLoRA(64, 8, 1.0, 42L);
        double[] v = new double[64];
        for (int i = 0; i < 64; i++) v[i] = i / 64.0;
        double[] a1 = l1.adapt(v);
        double[] a2 = l2.adapt(v);
        for (int i = 0; i < 64; i++) {
            assertThat(a1[i]).isCloseTo(a2[i], offset(1e-9));
        }
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
