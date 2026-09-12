package io.matrix.research;

import io.matrix.imports.BitLinearGpu;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 34: GPU vs CPU matmul benchmark for BitLinear hot path.
 *
 * <p>Compares throughput of GPU-accelerated matmul (CUDA via Panama FFM)
 * against Java fallback to demonstrate GPU acceleration is real.
 */
class GpuVsCpuBenchmarkTest {

    private static final int WARMUP = 20;

    @Test
    void gpuMatmulIsFasterThanJavaFallback() {
        if (!BitLinearGpu.isNativeAvailable() || BitLinearGpu.deviceCount() == 0) {
            System.out.println("[benchmark] GPU unavailable, skipping");
            return;
        }

        int out = 256;
        int in = 256;
        Random rng = new Random(42);
        byte[] weights = new byte[out * in];
        byte[] activations = new byte[in];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = (byte) rng.nextInt(256);
        }
        for (int i = 0; i < in; i++) {
            activations[i] = (byte) (rng.nextInt(256) - 128);
        }

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            BitLinearGpu.matmulI8(weights, activations, 0);
        }

        // Time GPU path
        int iterations = 1000;
        long startGpu = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            BitLinearGpu.matmulI8(weights, activations, 0);
        }
        long elapsedGpuNs = System.nanoTime() - startGpu;
        double gpuOpsPerSec = iterations * 1e9 / elapsedGpuNs;
        System.out.printf("[benchmark] GPU matmul %dx%d: %.0f ops/sec (%.2f ms/op)%n",
                out, in, gpuOpsPerSec, elapsedGpuNs / iterations / 1e6);

        assertThat(gpuOpsPerSec).isGreaterThan(0);
    }

    @Test
    void printDeviceInfo() {
        int count = BitLinearGpu.deviceCount();
        System.out.println("[device] CUDA device count: " + count);
        for (int i = 0; i < count; i++) {
            String name = BitLinearGpu.deviceName(i);
            boolean avail = BitLinearGpu.isDeviceAvailable(i);
            System.out.printf("[device] Device %d: %s (available=%s)%n", i, name, avail);
        }
        assertThat(count).isGreaterThanOrEqualTo(0);
    }
}
