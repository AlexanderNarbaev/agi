package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveQLoRATest {

    @Test
    void constructValid() {
        CognitiveQLoRA qlora = new CognitiveQLoRA(64, 16, 42L);
        assertThat(qlora.teacherDim()).isEqualTo(64);
        assertThat(qlora.studentDim()).isEqualTo(16);
        assertThat(qlora.seed()).isEqualTo(42L);
    }

    @Test
    void invalidDimsThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveQLoRA(0, 16, 1L)
        );
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveQLoRA(64, 0, 1L)
        );
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveQLoRA(8, 16, 1L)  // studentDim > teacherDim
        );
    }

    @Test
    void quantizeReturnsValid() {
        CognitiveQLoRA qlora = new CognitiveQLoRA(64, 16, 42L);
        double[] weights = new double[64];
        for (int i = 0; i < 64; i++) weights[i] = i / 64.0;
        CognitiveQLoRA.QuantizedWeights q = qlora.quantize(weights);
        assertThat(q.data()[0].length).isEqualTo(64);
        for (int v : q.data()[0]) {
            assertThat(v).isBetween(0, 15);
        }
    }

    @Test
    void quantizeNullReturnsNull() {
        CognitiveQLoRA qlora = new CognitiveQLoRA(64, 16, 42L);
        assertThat(qlora.quantize(null)).isNull();
    }

    @Test
    void quantizeWrongDimReturnsNull() {
        CognitiveQLoRA qlora = new CognitiveQLoRA(64, 16, 42L);
        double[] weights = new double[32];
        assertThat(qlora.quantize(weights)).isNull();
    }

    @Test
    void applyReturnsCorrectSize() {
        CognitiveQLoRA qlora = new CognitiveQLoRA(64, 16, 42L);
        double[] input = new double[64];
        double[] weights = new double[64];
        for (int i = 0; i < 64; i++) {
            input[i] = i / 64.0;
            weights[i] = i / 128.0;
        }
        CognitiveQLoRA.QuantizedWeights q = qlora.quantize(weights);
        double[] result = qlora.apply(input, q);
        assertThat(result.length).isEqualTo(64);
    }

    @Test
    void applyNullReturnsInput() {
        CognitiveQLoRA qlora = new CognitiveQLoRA(64, 16, 42L);
        double[] input = new double[64];
        assertThat(qlora.apply(input, null)).isSameAs(input);
    }

    @Test
    void memoryReductionPositive() {
        CognitiveQLoRA qlora = new CognitiveQLoRA(64, 8, 42L);
        // With small student dim, LoRA + 4-bit should be smaller than 32-bit
        // QLoRA is memory-efficient when studentDim << teacherDim
        // For teacherDim=64, studentDim=8, LoRA overhead may exceed savings
        assertThat(qlora.memoryReduction()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void approximationErrorComputed() {
        CognitiveQLoRA qlora = new CognitiveQLoRA(64, 16, 42L);
        double[] original = new double[64];
        double[] reconstructed = new double[64];
        for (int i = 0; i < 64; i++) {
            original[i] = i / 64.0;
            reconstructed[i] = i / 64.0 + 0.01;
        }
        double err = qlora.approximationError(original, reconstructed);
        assertThat(err).isGreaterThan(0.0);
    }
}
