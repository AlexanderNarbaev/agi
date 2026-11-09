package io.matrix.nativekernel;

import io.matrix.bir.TtForm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * W51 — the equivalence of the Java reference and the native path, and the
 * behaviour of the boundary when a caller misuses it.
 *
 * <p>The tests are written so that they pass whether or not the shared
 * object was built: with the library they compare the two paths bit for bit,
 * without it they assert that the Java path is selected and that it agrees
 * with the BIR reference. A missing C toolchain is therefore not a test
 * failure but a different branch of the same contract.
 */
class NativeKernelEquivalenceTest {

    /** The seed of the random inputs; fixed, so a failure is reproducible. */
    private static final long RANDOM_SEED = 20261108L;

    /** The smallest arity a truth table may have. */
    private static final int MIN_ARITY = 1;

    /** The largest arity a truth table may have. */
    private static final int MAX_ARITY = 20;

    /** An arity one above the largest the native path supports. */
    private static final int ARITY_ABOVE_MAXIMUM = MAX_ARITY + 1;

    /** The number of random rows the randomised comparison uses. */
    private static final int RANDOM_ROWS = 4096;

    /** The number of random rows the small randomised comparison uses. */
    private static final int FEW_RANDOM_ROWS = 37;

    // ── (a) deterministic boundary cases ─────────────────────────────────────

    @Test
    @DisplayName("the smallest arity: the native path agrees with the reference")
    void theSmallestArity() {
        TtForm form = constantForm(MIN_ARITY, false);
        assertEquivalent(form, new long[]{0L, 1L, 1L, 0L});
    }

    @Test
    @DisplayName("the largest arity: the native path agrees with the reference")
    void theLargestArity() {
        TtForm form = parityForm(MAX_ARITY);
        Random random = new Random(RANDOM_SEED);
        long[] inputs = new long[128];
        long mask = (1L << MAX_ARITY) - 1L;
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = random.nextLong() & mask;
        }
        assertEquivalent(form, inputs);
    }

    @Test
    @DisplayName("an all-zero table: the native path agrees with the reference")
    void anAllZeroTable() {
        TtForm form = constantForm(8, false);
        assertEquivalent(form, new long[]{0L, 1L, 2L, 3L, 255L, 256L});
    }

    @Test
    @DisplayName("an all-one table: the native path agrees with the reference")
    void anAllOneTable() {
        TtForm form = constantForm(8, true);
        assertEquivalent(form, new long[]{0L, 1L, 2L, 3L, 255L, 256L});
    }

    @Test
    @DisplayName("an empty batch: the native path agrees with the reference")
    void anEmptyBatch() {
        TtForm form = parityForm(4);
        assertEquivalent(form, new long[0]);
    }

    @Test
    @DisplayName("a single row: the native path agrees with the reference")
    void aSingleRow() {
        TtForm form = parityForm(4);
        assertEquivalent(form, new long[]{9L});
    }

    @Test
    @DisplayName("input bits above the arity are ignored, exactly as the reference ignores them")
    void inputBitsAboveTheArityAreIgnored() {
        TtForm form = parityForm(4);
        // The reference masks the input with ((1L << k) - 1), so the two rows
        // below are the same row for a four-bit function.
        assertEquivalent(form, new long[]{5L, 5L | (1L << 20), 5L | (1L << 40)});
    }

    // ── (b) randomised comparison, with a fixed seed ─────────────────────────

    @Test
    @DisplayName("random rows: the native path agrees with the reference, row for row")
    void randomRowsAgree() {
        TtForm form = parityForm(12);
        long[] inputs = randomInputs(RANDOM_ROWS, 12);
        assertEquivalent(form, inputs);
    }

    @Test
    @DisplayName("random rows of an odd length: the native path agrees with the reference")
    void randomRowsOfAnOddLengthAgree() {
        TtForm form = parityForm(9);
        long[] inputs = randomInputs(FEW_RANDOM_ROWS, 9);
        assertEquivalent(form, inputs);
    }

    @Test
    @DisplayName("the random comparison is repeatable: the same seed gives the same rows")
    void theRandomComparisonIsRepeatable() {
        long[] first = randomInputs(FEW_RANDOM_ROWS, 9);
        long[] second = randomInputs(FEW_RANDOM_ROWS, 9);
        assertThat(first).isEqualTo(second);
    }

    // ── (c) invalid input: a refusal, never a crash ─────────────────────────

    @Test
    @DisplayName("an arity of zero is refused with a status code, and the JVM survives")
    void anArityOfZeroIsRefused() {
        assumeNativeIsAvailable();
        assertThatCode(() -> {
            int status = callNative(0, 1, 4);
            assertThat(status).isEqualTo(NativeKernelStatus.ERR_ARITY);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an arity above the maximum is refused with a status code")
    void anArityAboveTheMaximumIsRefused() {
        assumeNativeIsAvailable();
        assertThatCode(() -> {
            int status = callNative(ARITY_ABOVE_MAXIMUM, 1, 4);
            assertThat(status).isEqualTo(NativeKernelStatus.ERR_ARITY);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a truth table that is too short for the arity is refused")
    void aShortTruthTableIsRefused() {
        assumeNativeIsAvailable();
        assertThatCode(() -> {
            // Four words cannot hold the 2^16 bits a sixteen-bit function needs.
            int status = callNative(16, 4, 4);
            assertThat(status).isEqualTo(NativeKernelStatus.ERR_TABLE);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a negative batch count is refused")
    void aNegativeBatchCountIsRefused() {
        assumeNativeIsAvailable();
        assertThatCode(() -> {
            int status = callNative(4, 1, -1);
            assertThat(status).isEqualTo(NativeKernelStatus.ERR_COUNT);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a null buffer is refused")
    void aNullBufferIsRefused() {
        assumeNativeIsAvailable();
        assertThatCode(() -> {
            NativeKernelLoader loader = NativeKernelLoader.instance();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment inSegment = arena.allocate(ValueLayout.JAVA_LONG, 1);
                MemorySegment outSegment = arena.allocate(ValueLayout.JAVA_LONG, 1);
                int status = loader.evalBatch(MemorySegment.NULL, 1, 4, inSegment, outSegment, 1);
                assertThat(status).isEqualTo(NativeKernelStatus.ERR_NULL);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a misaligned buffer is refused rather than read")
    void aMisalignedBufferIsRefused() {
        assumeNativeIsAvailable();
        assertThatCode(() -> {
            NativeKernelLoader loader = NativeKernelLoader.instance();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment aligned = arena.allocate(64);
                MemorySegment misaligned = aligned.asSlice(1);
                MemorySegment outSegment = arena.allocate(ValueLayout.JAVA_LONG, 1);
                int status = loader.evalBatch(misaligned, 1, 4, misaligned, outSegment, 1);
                assertThat(status).isEqualTo(NativeKernelStatus.ERR_ALIGNMENT);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the status codes have names, so a refusal is not a mystery")
    void theStatusCodesHaveNames() {
        assertThat(NativeKernelStatus.describe(NativeKernelStatus.OK)).isEqualTo("ok");
        assertThat(NativeKernelStatus.describe(NativeKernelStatus.ERR_ARITY))
                .isEqualTo("arity out of range");
        assertThat(NativeKernelStatus.describe(NativeKernelStatus.ERR_TABLE))
                .isEqualTo("truth table too short");
        assertThat(NativeKernelStatus.describe(NativeKernelStatus.ERR_COUNT))
                .isEqualTo("batch count out of range");
        assertThat(NativeKernelStatus.describe(NativeKernelStatus.ERR_NULL))
                .isEqualTo("null argument");
        assertThat(NativeKernelStatus.describe(NativeKernelStatus.ERR_ALIGNMENT))
                .isEqualTo("buffer not aligned");
        assertThat(NativeKernelStatus.describe(-999)).isEqualTo("unknown status");
    }

    // ── (e) the fallback ─────────────────────────────────────────────────────

    @Test
    @DisplayName("without the shared object the Java path is chosen and it agrees with the reference")
    void withoutTheLibraryTheJavaPathIsChosen() {
        NativeKernelLoader missing = NativeKernelLoader.loadFrom(
                java.nio.file.Path.of("/nonexistent/w51/libmatrixkernel.so"));
        assertThat(missing.isAvailable()).isFalse();
        assertThat(missing.unavailableReason()).isNotBlank();

        TtForm form = parityForm(8);
        try (TruthTableNativeKernel kernel = new TruthTableNativeKernel(form, missing)) {
            assertThat(kernel.selectedPath(RANDOM_ROWS)).isEqualTo(NativeKernelPolicy.Path.JAVA);
            long[] inputs = randomInputs(RANDOM_ROWS, 8);
            assertThat(kernel.evaluateBatch(inputs))
                    .isEqualTo(kernel.evaluateBatchJavaReference(inputs));
        }
    }

    @Test
    @DisplayName("a loader that points at a directory is refused, not loaded")
    void aLoaderThatPointsAtADirectoryIsRefused() {
        NativeKernelLoader missing = NativeKernelLoader.loadFrom(java.nio.file.Path.of("/tmp"));
        assertThat(missing.isAvailable()).isFalse();
        assertThat(missing.unavailableReason()).contains("/tmp");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Asserts that the native path, the flat Java path and the BIR reference
     * all give the same answers.
     *
     * @param form   the truth table.
     * @param inputs the packed input rows.
     */
    private static void assertEquivalent(TtForm form, long[] inputs) {
        try (TruthTableNativeKernel kernel = new TruthTableNativeKernel(form)) {
            long[] reference = kernel.evaluateBatchJavaReference(inputs);
            assertThat(kernel.evaluateBatchJavaFlat(inputs)).isEqualTo(reference);
            assertThat(kernel.evaluateBatch(inputs)).isEqualTo(reference);
            if (kernel.isNativeAvailable()) {
                assertThat(kernel.evaluateBatchNative(inputs)).isEqualTo(reference);
            }
        }
    }

    /**
     * Skips the test when the shared object was not built.
     *
     * <p>The assumption is taken OUTSIDE the assertion on purpose: an
     * assumption that is raised inside {@code assertThatCode} would be caught
     * by it and reported as a failure rather than as a skip.
     */
    private static void assumeNativeIsAvailable() {
        NativeKernelLoader loader = NativeKernelLoader.instance();
        assumeTrue(loader.isAvailable(),
                "the shared object was not built, so the native boundary cannot be exercised");
    }

    /**
     * Calls the native entry point with deliberately wrong arguments.
     *
     * @param arity      the arity to claim.
     * @param tableWords the number of table words to claim.
     * @param count      the batch count to claim.
     * @return the native status code.
     */
    private static int callNative(int arity, int tableWords, int count) {
        NativeKernelLoader loader = NativeKernelLoader.instance();
        if (!loader.isAvailable()) {
            throw new IllegalStateException("the shared object was not built: "
                    + loader.unavailableReason());
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment table = arena.allocate(ValueLayout.JAVA_LONG, Math.max(tableWords, 1));
            MemorySegment inSegment = arena.allocate(ValueLayout.JAVA_LONG, Math.max(count, 1));
            MemorySegment outSegment = arena.allocate(ValueLayout.JAVA_LONG, Math.max(count, 1));
            return loader.evalBatch(table, tableWords, arity, inSegment, outSegment, count);
        }
    }

    private static long[] randomInputs(int rows, int arity) {
        Random random = new Random(RANDOM_SEED);
        long mask = (1L << arity) - 1L;
        long[] inputs = new long[rows];
        for (int i = 0; i < rows; i++) {
            inputs[i] = random.nextLong() & mask;
        }
        return inputs;
    }

    private static TtForm constantForm(int arity, boolean value) {
        int cells = 1 << arity;
        long[] table = new long[(cells + 63) / 64];
        if (value) {
            for (int i = 0; i < cells; i++) {
                table[i >>> 6] |= 1L << (i & 63);
            }
        }
        return new TtForm(arity, table, "w51-test", 1.0);
    }

    private static TtForm parityForm(int arity) {
        int cells = 1 << arity;
        long[] table = new long[(cells + 63) / 64];
        for (int i = 0; i < cells; i++) {
            if (Integer.bitCount(i) % 2 == 1) {
                table[i >>> 6] |= 1L << (i & 63);
            }
        }
        return new TtForm(arity, table, "w51-test", 1.0);
    }
}
