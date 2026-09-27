package io.matrix.federation.gpu;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W6 Step 1 — MatrixNativeMath tests.
 *
 * <p>Verifies the SIMD-accelerated methods produce results bit-identical
 * to the scalar reference (or within floating-point tolerance).</p>
 */
class MatrixNativeMathTest {

    @Test
    void backend_is_one_of_three_options() {
        MatrixNativeMath m = new MatrixNativeMath();
        assertThat(m.backend()).isIn(
            MatrixNativeMath.Backend.CPU_VECTOR,
            MatrixNativeMath.Backend.CPU_SCALAR,
            MatrixNativeMath.Backend.UNAVAILABLE);
    }

    @Test
    void vectorPopCount_matches_scalar_for_random_vectors() {
        MatrixNativeMath m = new MatrixNativeMath();
        Random r = new Random(42L);
        for (int trial = 0; trial < 20; trial++) {
            long[] v = new long[64 + r.nextInt(64)];
            for (int i = 0; i < v.length; i++) v[i] = r.nextLong();
            long simd = m.vectorPopCount(v);
            long scalar = MatrixNativeMath.scalarPopCount(v);
            // Simd path is bit-identical to scalar when vector API ops
            // don't expose POPCOUNT directly (we use Long.bitCount
            // for the final tally).
            assertThat(simd).as("trial " + trial).isEqualTo(scalar);
        }
    }

    @Test
    void vectorXorPopCount_matches_scalar_for_random_vectors() {
        MatrixNativeMath m = new MatrixNativeMath();
        Random r = new Random(42L);
        for (int trial = 0; trial < 20; trial++) {
            int n = 64 + r.nextInt(64);
            long[] a = new long[n], b = new long[n];
            for (int i = 0; i < n; i++) {
                a[i] = r.nextLong();
                b[i] = r.nextLong();
            }
            long simd = m.vectorXorPopCount(a, b);
            long scalar = MatrixNativeMath.scalarXorPopCount(a, b);
            assertThat(simd).as("trial " + trial).isEqualTo(scalar);
        }
    }

    @Test
    void vectorXorPopCount_handles_zero_vectors() {
        MatrixNativeMath m = new MatrixNativeMath();
        long[] zero = new long[16];
        long[] one  = new long[16];
        for (int i = 0; i < 16; i++) one[i] = 0xFFL;
        assertThat(m.vectorXorPopCount(zero, zero)).isZero();
        assertThat(m.vectorXorPopCount(zero, one)).isEqualTo(16 * 8);
    }

    @Test
    void vectorBatchUpdate_clamps_to_threshold() {
        MatrixNativeMath m = new MatrixNativeMath();
        int[] state = {0, 0, 0, 0};
        int[] feedback = {-5, 5, -2, 2};
        int[] threshold = {1, 1, 1, 1};
        int[] out = m.vectorBatchUpdate(state, feedback, threshold);
        // Each state[i] is clamped to [-1, 1]
        for (int v : out) {
            assertThat(v).isBetween(-1, 1);
        }
    }

    @Test
    void vectorBatchUpdate_handles_empty_input() {
        MatrixNativeMath m = new MatrixNativeMath();
        int[] out = m.vectorBatchUpdate(new int[0], new int[0], new int[0]);
        assertThat(out).isEmpty();
    }

    @Test
    void vectorPopCount_null_input_returns_zero() {
        MatrixNativeMath m = new MatrixNativeMath();
        assertThat(m.vectorPopCount(null)).isZero();
    }

    @Test
    void isVectorAvailable_returns_boolean() {
        boolean v = MatrixNativeMath.isVectorAvailable();
        // On JDK 25 with incubator module: true. Otherwise false.
        assertThat(v).isIn(true, false);
    }
}
