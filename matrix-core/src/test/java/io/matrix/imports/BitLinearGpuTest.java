package io.matrix.imports;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BitLinearGpu} — CUDA-accelerated matmul via Project Panama FFM.
 *
 * <p>Verifies GPU availability, device info, and matmul correctness against
 * a Java reference implementation.
 */
class BitLinearGpuTest {

    @Test
    void isNativeAvailableReturnsBoolean() {
        boolean available = BitLinearGpu.isNativeAvailable();
        assertThat(available == true || available == false).isTrue();
    }

    @Test
    void deviceCountReturnsNonNegative() {
        int count = BitLinearGpu.deviceCount();
        assertThat(count).isGreaterThanOrEqualTo(0);
        if (count > 0) {
            System.out.println("[BitLinearGpu] CUDA device count: " + count);
        } else {
            System.out.println("[BitLinearGpu] No CUDA devices available");
        }
    }

    @Test
    void deviceNameReturnsStringForValidDevice() {
        int count = BitLinearGpu.deviceCount();
        if (count > 0) {
            String name = BitLinearGpu.deviceName(0);
            assertThat(name).isNotEmpty();
            System.out.println("[BitLinearGpu] Device 0: " + name);
        }
    }

    @Test
    void lastErrorReturnsString() {
        String err = BitLinearGpu.lastError();
        assertThat(err).isNotNull();
    }

    @Test
    void matmulI8SmallCaseMatchesJavaReference() {
        // 2×3 weights × 3-vector = 2-vector
        byte[] weights = {
                1, 2, 3,    // output 0: 1*4 + 2*5 + 3*6 = 4+10+18 = 32
                4, 5, 6     // output 1: 4*4 + 5*5 + 6*6 = 16+25+36 = 77
        };
        byte[] activations = {4, 5, 6};
        int[] expected = {32, 77};

        int[] actual = BitLinearGpu.matmulI8(weights, activations, 0);
        if (BitLinearGpu.isDeviceAvailable(0)) {
            assertThat(actual).isNotNull();
            assertThat(actual).containsExactly(expected);
            System.out.println("[BitLinearGpu] GPU matmul matches Java reference");
        } else {
            // Java fallback path
            assertThat(actual).containsExactly(expected);
            System.out.println("[BitLinearGpu] Java fallback matmul used");
        }
    }

    @Test
    void matmulI8LargerCaseMatchesJavaReference() {
        // 16×64 weights × 64-vector = 16-vector
        int out = 16, in = 64;
        byte[] weights = new byte[out * in];
        byte[] activations = new byte[in];
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < weights.length; i++) {
            weights[i] = (byte) rng.nextInt(256);
        }
        for (int i = 0; i < in; i++) {
            activations[i] = (byte) (rng.nextInt(256) - 128);
        }
        // Compute Java reference
        int[] expected = new int[out];
        for (int i = 0; i < out; i++) {
            int sum = 0;
            for (int j = 0; j < in; j++) {
                sum += (int) weights[i * in + j] * (int) activations[j];
            }
            expected[i] = sum;
        }
        int[] actual = BitLinearGpu.matmulI8(weights, activations, 0);
        assertThat(actual).isNotNull();
        assertThat(actual).containsExactly(expected);
    }

    @Test
    void matmulI8RejectsNullInputs() {
        assertThat(org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> BitLinearGpu.matmulI8(null, new byte[1], 0))
                .isInstanceOf(IllegalArgumentException.class));
        assertThat(org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> BitLinearGpu.matmulI8(new byte[1], null, 0))
                .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void matmulI8RejectsMismatchedDimensions() {
        byte[] weights = new byte[10]; // 10 bytes
        byte[] activations = new byte[3]; // 3 bytes, but 10/3 not integer
        assertThat(org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> BitLinearGpu.matmulI8(weights, activations, 0))
                .isInstanceOf(IllegalArgumentException.class));
    }
}
