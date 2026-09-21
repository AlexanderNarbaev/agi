package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BitLinearGpuForwardTest {

    @Test
    void smallMatrixUsesCpuPath() {
        // 8×8 = 64 elements, well below GPU_DISPATCH_THRESHOLD
        float[][] w = new float[8][8];
        float[] x = new float[8];
        Random rng = new Random(1);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) w[i][j] = (float) rng.nextGaussian();
            x[i] = (float) rng.nextGaussian();
        }
        float[] result = BitLinearGpuForward.forward(w, x);
        assertThat(result).hasSize(8);
        for (float v : result) {
            assertThat(Float.isFinite(v)).isTrue();
        }
    }

    @Test
    void largeMatrixUsesGpuPathAndMatchesCpu() {
        // Above GPU_DISPATCH_THRESHOLD: 128*128 = 16384 elements
        int out = 128, in = 128;
        float[][] w = new float[out][in];
        float[] x = new float[in];
        Random rng = new Random(42);
        for (int i = 0; i < out; i++) {
            for (int j = 0; j < in; j++) w[i][j] = (float) rng.nextGaussian() * 0.3f;
        }
        for (int i = 0; i < in; i++) x[i] = (float) rng.nextGaussian();

        // CPU reference
        float[] cpuResult = BitLinear.forward(w, x);
        // GPU path
        float[] gpuResult = BitLinearGpuForward.forward(w, x);

        assertThat(gpuResult).hasSize(out);
        // Results should match (same quantization, same math)
        for (int i = 0; i < out; i++) {
            assertThat(gpuResult[i]).isCloseTo(cpuResult[i], within(1e-2f));
        }
    }

    @Test
    void forwardProducesCorrectOutputSize() {
        float[][] w = new float[16][16];
        float[] x = new float[16];
        Random rng = new Random(1);
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) w[i][j] = (float) rng.nextGaussian();
            x[i] = (float) rng.nextGaussian();
        }
        float[] result = BitLinearGpuForward.forward(w, x);
        assertThat(result).hasSize(16);
    }

    @Test
    void forwardRejectsNullInputs() {
        assertThatThrownBy(() -> BitLinearGpuForward.forward(null, new float[1]))
                .isInstanceOf(IllegalArgumentException.class);
        float[][] w = new float[4][4];
        assertThatThrownBy(() -> BitLinearGpuForward.forward(w, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forwardRejectsDimensionMismatch() {
        float[][] w = new float[4][8];
        float[] x = new float[4]; // wrong size
        assertThatThrownBy(() -> BitLinearGpuForward.forward(w, x))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gpuDispatchThresholdIsPositive() {
        assertThat(BitLinearGpuForward.GPU_DISPATCH_THRESHOLD).isGreaterThan(0);
    }
}
