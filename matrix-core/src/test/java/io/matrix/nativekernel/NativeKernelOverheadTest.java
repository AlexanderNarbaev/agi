package io.matrix.nativekernel;

import io.matrix.bir.TtForm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * W51 — the cost of crossing the language boundary, measured.
 *
 * <p>The test measures three quantities separately, because they are
 * different things and they are confused with each other easily:
 * <ul>
 *   <li>the MARSHALLING: the arena, the copy of the inputs into native
 *       memory and the copy of the results back;</li>
 *   <li>the CALL: the downcall itself, with a payload of one row;</li>
 *   <li>the ARITHMETIC: the truth-table lookup, in C.</li>
 * </ul>
 *
 * <p>It then reports the batch size at which the native path overtakes the
 * flat Java path, which is the question a caller actually has.
 *
 * <p><b>What this test asserts and what it does not.</b> It asserts the
 * relations the measurement shows with a wide margin — the native path is
 * slower for a single row, and it is much faster than the BIR reference API
 * for a large batch — and it reports everything else. It does NOT assert a
 * timing equality: a test that fails when the machine is busy is a test that
 * trains its reader to ignore it. Every measurement is the BEST of several
 * timed rounds, which is what makes the reported numbers stable enough to
 * compare. The authoritative numbers are produced by
 * {@code scripts/w51-native-kernel-bench.sh} and are recorded in
 * {@code docs-v2/engineering/W51-JAVA-C-BOUNDARY.md}.
 */
class NativeKernelOverheadTest {

    /** The seed of the random inputs; fixed, so a failure is reproducible. */
    private static final long RANDOM_SEED = 20261108L;

    /** The arity of the measured function. */
    private static final int ARITY = 16;

    /** The batch size of the single-row measurement. */
    private static final int ONE_ROW = 1;

    /** The batch size of the large-batch measurement. */
    private static final int LARGE_BATCH = 65536;

    /** How many calls one measurement pass makes at the large batch size. */
    private static final int LARGE_BATCH_CALLS = 64;

    /** How many calls one measurement pass makes at the single-row size. */
    private static final int ONE_ROW_CALLS = 20000;

    /** How many timed rounds each measurement takes; the best one is kept. */
    private static final int TIMED_ROUNDS = 5;

    /** The factor the full native call may exceed the marshalling alone by. */
    private static final double MAX_CROSSING_FACTOR = 4.0;

    /** How much faster than the BIR reference API the native path has to be. */
    private static final double MIN_SPEEDUP_OVER_REFERENCE = 5.0;

    /** The batch sizes the crossover is searched over. */
    private static final int[] CROSSOVER_BATCHES = {1, 8, 64, 256, 1024, 4096, 16384, 65536};

    @Test
    @DisplayName("the cost of the crossing is measured: marshalling, call and arithmetic")
    void theCostOfTheCrossingIsMeasured() {
        assumeTrue(NativeKernelLoader.instance().isAvailable(),
                "the shared object was not built, so the crossing cannot be measured");
        TtForm form = parityForm();
        try (TruthTableNativeKernel kernel = new TruthTableNativeKernel(form)) {
            long[] rows = randomInputs(LARGE_BATCH, ARITY);

            double marshallingNs = perCallNs(measure(() -> kernel.measureMarshallingOnly(rows),
                    LARGE_BATCH_CALLS), LARGE_BATCH_CALLS);
            double callOnlyNs = perCallNs(measure(() -> kernel.measureNativeCallOnly(rows),
                    LARGE_BATCH_CALLS), LARGE_BATCH_CALLS);
            double fullNs = perCallNs(measure(() -> kernel.evaluateBatchNative(rows),
                    LARGE_BATCH_CALLS), LARGE_BATCH_CALLS);

            System.out.printf("[W51] batch=%d marshalling=%.1f ns, marshalling+downcall=%.1f ns, "
                            + "full native=%.1f ns, C arithmetic=%.1f ns (%.3f ns/row)%n",
                    LARGE_BATCH, marshallingNs, callOnlyNs, fullNs,
                    fullNs - callOnlyNs, (fullNs - callOnlyNs) / LARGE_BATCH);

            assertThat(marshallingNs).isPositive();
            assertThat(callOnlyNs).isPositive();
            assertThat(fullNs).isPositive();
            // The crossing costs a bounded multiple of the marshalling alone:
            // the native path is not paying for itself with an order of
            // magnitude of overhead, it is paying for itself with a factor.
            assertThat(fullNs).isLessThan(marshallingNs * MAX_CROSSING_FACTOR);
        }
    }

    @Test
    @DisplayName("one row across the boundary costs more than one row in Java")
    void oneRowAcrossTheBoundaryCostsMoreThanOneRowInJava() {
        assumeTrue(NativeKernelLoader.instance().isAvailable(),
                "the shared object was not built, so the crossing cannot be measured");
        TtForm form = parityForm();
        try (TruthTableNativeKernel kernel = new TruthTableNativeKernel(form)) {
            long[] row = randomInputs(ONE_ROW, ARITY);
            double nativeNs = perCallNs(measure(() -> kernel.evaluateBatchNative(row),
                    ONE_ROW_CALLS), ONE_ROW_CALLS);
            double javaNs = perCallNs(measure(() -> kernel.evaluateBatchJavaFlat(row),
                    ONE_ROW_CALLS), ONE_ROW_CALLS);
            System.out.printf("[W51] one row: native=%.1f ns, java flat=%.1f ns%n", nativeNs, javaNs);
            assertThat(nativeNs).isGreaterThan(javaNs);
        }
    }

    @Test
    @DisplayName("the native path is much faster than the BIR reference API for a large batch")
    void theNativePathIsMuchFasterThanTheReferenceApiForALargeBatch() {
        assumeTrue(NativeKernelLoader.instance().isAvailable(),
                "the shared object was not built, so the crossing cannot be measured");
        TtForm form = parityForm();
        try (TruthTableNativeKernel kernel = new TruthTableNativeKernel(form)) {
            long[] rows = randomInputs(LARGE_BATCH, ARITY);
            double referenceNs = perCallNs(measure(() -> kernel.evaluateBatchJavaReference(rows), 4), 4);
            double nativeNs = perCallNs(measure(() -> kernel.evaluateBatchNative(rows),
                    LARGE_BATCH_CALLS), LARGE_BATCH_CALLS);
            System.out.printf("[W51] batch=%d java reference=%.1f ns, native=%.1f ns, ratio=%.2f%n",
                    LARGE_BATCH, referenceNs, nativeNs, referenceNs / nativeNs);
            assertThat(referenceNs / nativeNs).isGreaterThan(MIN_SPEEDUP_OVER_REFERENCE);
        }
    }

    @Test
    @DisplayName("the batch size at which the native path overtakes the flat Java path is reported")
    void theCrossoverIsReported() {
        assumeTrue(NativeKernelLoader.instance().isAvailable(),
                "the shared object was not built, so the crossing cannot be measured");
        TtForm form = parityForm();
        try (TruthTableNativeKernel kernel = new TruthTableNativeKernel(form)) {
            int crossover = -1;
            int measured = 0;
            System.out.println("[W51] native against the flat Java path, batch by batch:");
            for (int batch : CROSSOVER_BATCHES) {
                long[] rows = randomInputs(batch, ARITY);
                int calls = Math.max(16, LARGE_BATCH_CALLS * LARGE_BATCH / batch / 64);
                double nativeNs = perCallNs(measure(() -> kernel.evaluateBatchNative(rows), calls), calls);
                double javaNs = perCallNs(measure(() -> kernel.evaluateBatchJavaFlat(rows), calls), calls);
                boolean nativeWins = nativeNs <= javaNs;
                measured++;
                System.out.printf("[W51]   batch=%-7d native=%10.1f ns  java-flat=%10.1f ns  %s%n",
                        batch, nativeNs, javaNs, nativeWins ? "native wins" : "java wins");
                if (nativeWins && crossover < 0) {
                    crossover = batch;
                }
            }
            System.out.println("[W51] the native path overtakes the flat Java path at batch "
                    + (crossover < 0 ? "none of the measured sizes" : String.valueOf(crossover)));
            // The crossover is a MEASUREMENT, not a contract: the machine it
            // was measured on is not the machine it will run on. What is
            // asserted is that the comparison was actually made.
            assertThat(measured).isEqualTo(CROSSOVER_BATCHES.length);
        }
    }

    @Test
    @DisplayName("the policy that is in force keeps every batch on the Java path")
    void thePolicyInForceKeepsEveryBatchOnTheJavaPath() {
        TtForm form = parityForm();
        try (TruthTableNativeKernel kernel = new TruthTableNativeKernel(form)) {
            for (int batch : CROSSOVER_BATCHES) {
                assertThat(kernel.selectedPath(batch)).isEqualTo(NativeKernelPolicy.Path.JAVA);
            }
        }
    }

    /**
     * Measures the time of a number of calls and keeps the best round.
     *
     * @param call  the call to make.
     * @param calls how many times to make it in one round.
     * @return the time of the fastest round, in nanoseconds.
     */
    private static long measure(Runnable call, int calls) {
        long best = Long.MAX_VALUE;
        for (int round = 0; round < TIMED_ROUNDS; round++) {
            call.run();
            long start = System.nanoTime();
            for (int i = 0; i < calls; i++) {
                call.run();
            }
            long elapsed = System.nanoTime() - start;
            best = Math.min(best, elapsed);
        }
        return best;
    }

    private static double perCallNs(long totalNanos, int calls) {
        return (double) totalNanos / calls;
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

    private static TtForm parityForm() {
        int cells = 1 << ARITY;
        long[] table = new long[(cells + 63) / 64];
        for (int i = 0; i < cells; i++) {
            if (Integer.bitCount(i) % 2 == 1) {
                table[i >>> 6] |= 1L << (i & 63);
            }
        }
        return new TtForm(ARITY, table, "w51-overhead-test", 1.0);
    }
}
