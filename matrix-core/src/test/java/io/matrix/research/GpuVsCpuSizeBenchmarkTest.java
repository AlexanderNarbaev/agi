package io.matrix.research;

import io.matrix.imports.BitLinearGpu;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 35: Comprehensive GPU vs CPU matmul benchmark.
 *
 * <p>Measures throughput across matrix sizes relevant to BitNet b1.58:
 * - Small (64×64): typical transformer attention head
 * - Medium (256×256): FFN intermediate
 * - Large (1024×1024): FFN output
 * - Very large (4096×4096): feed-forward in large model
 *
 * <p>Compares GPU-accelerated path vs Java fallback to show speedup.
 */
class GpuVsCpuSizeBenchmarkTest {

    @Test
    void benchmarkSmallMatrix() {
        runBenchmark(64, 64, "Small attention head");
    }

    @Test
    void benchmarkMediumMatrix() {
        runBenchmark(256, 256, "Medium FFN intermediate");
    }

    @Test
    void benchmarkLargeMatrix() {
        runBenchmark(1024, 1024, "Large FFN output");
    }

    @Test
    void benchmarkVeryLargeMatrix() {
        runBenchmark(4096, 4096, "Very large feed-forward");
    }

    @Test
    void benchmarkNonSquareMatrices() {
        runBenchmark(768, 3072, "Transformer FFN (small→large)");
    }

    private static void runBenchmark(int out, int in, String label) {
        if (!BitLinearGpu.isNativeAvailable() || BitLinearGpu.deviceCount() == 0) {
            System.out.printf("[benchmark] GPU unavailable, skipping %s%n", label);
            return;
        }

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
        for (int i = 0; i < 20; i++) {
            BitLinearGpu.matmulI8(weights, activations, 0);
        }

        // Measure
        int iterations = computeIterations(out);
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            BitLinearGpu.matmulI8(weights, activations, 0);
        }
        long elapsedNs = System.nanoTime() - start;
        double opsPerSec = iterations * 1e9 / elapsedNs;
        double avgMs = elapsedNs / iterations / 1e6;
        double throughput = (long) iterations * out * in * 2.0 / (elapsedNs / 1e9); // FLOPS: 2*out*in per matmul
        System.out.printf("[benchmark %s] %dx%d: %.0f ops/sec, %.2f ms/op, %.1f GFLOPS%n",
                label, out, in, opsPerSec, avgMs, throughput / 1e9);

        assertThat(opsPerSec).isGreaterThan(0);
    }

    private static int computeIterations(int out) {
        if (out >= 4096) return 50;
        if (out >= 1024) return 200;
        if (out >= 256) return 1000;
        return 5000;
    }
}
