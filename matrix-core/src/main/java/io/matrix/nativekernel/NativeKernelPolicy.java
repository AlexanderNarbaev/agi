package io.matrix.nativekernel;

/**
 * W51 — the NAMED policy that decides whether the native path or the Java
 * path evaluates a batch.
 *
 * <p><b>The policy is a decision, and it is written down.</b> "Always native"
 * and "always Java" are both decisions; this class is the statement of which
 * one is in force and under what conditions it changes.
 *
 * <h2>The decision this class carries</h2>
 * <p>The MEASURED default is {@link Policy#DEFAULT}, and it selects the Java
 * path. The measurement is in
 * {@code docs-v2/engineering/W51-JAVA-C-BOUNDARY.md}; in one line: for this
 * kernel the crossing of the language boundary costs more than the
 * arithmetic it replaces. At a batch of 1024 rows the whole native call took
 * about 1400 ns, of which about 970 ns was the arena and the two copies and
 * about 410 ns was the C arithmetic; the same arithmetic in Java, called on
 * the same flat data, took about 1060 ns. The native path is 17 to 20 times
 * faster than the BIR reference API — but only because that API makes the
 * caller build two-dimensional arrays — and it is NOT faster than the
 * reference code called on the caller's own data.
 *
 * <p>So the native path is measured, tested and reachable, but it is not
 * selected. The switch is {@link Policy#nativeEnabled()}, and turning it on
 * is a decision that requires a new measurement, not a configuration change.
 *
 * <h2>What the policy never does</h2>
 * <p>It never depends on a wall clock, on a random number, or on anything
 * measured at run time. The same inputs give the same path, every time.
 */
public final class NativeKernelPolicy {

    /** The smallest arity a truth table may have; mirrors the C library. */
    public static final int MIN_ARITY = 1;

    /** The largest arity a truth table may have; mirrors io.matrix.bir.TtForm. */
    public static final int MAX_ARITY = 20;

    /**
     * The largest batch the native path accepts.
     *
     * <p>It mirrors {@code MATRIX_KERNEL_MAX_BATCH} in the C header. A batch
     * above it is refused by the native side with a status code, so the
     * policy keeps the Java path for such a batch rather than provoking a
     * refusal.
     */
    public static final int MAX_BATCH_FOR_NATIVE = 1 << 24;

    /**
     * The batch size at which the native path stopped losing to the flat Java
     * path in the W51 measurement.
     *
     * <p>MEASURED on the reference host: at this batch size the native path
     * was 1.02 times the flat Java path — a difference inside the noise — and
     * above it the Java path was ahead again. The value is recorded because a
     * threshold has to be somewhere, and because it is the number a future
     * measurement has to beat.
     */
    public static final int MEASURED_MIN_BATCH_FOR_NATIVE = 4096;

    /**
     * The policy in force.
     *
     * @param nativeEnabled     whether the native path may be selected at all.
     * @param minBatchForNative the smallest batch that may be evaluated
     *                           natively when it is enabled.
     */
    public record Policy(boolean nativeEnabled, int minBatchForNative) {

        /**
         * A policy that never selects the native path.
         *
         * @return the policy that keeps every batch on the Java path.
         */
        public static Policy javaOnly() {
            return new Policy(false, MEASURED_MIN_BATCH_FOR_NATIVE);
        }

        /**
         * A policy that may select the native path above a batch size.
         *
         * @param minBatchForNative the smallest batch that may be evaluated
         *                           natively.
         * @return the policy.
         */
        public static Policy nativeAbove(int minBatchForNative) {
            return new Policy(true, minBatchForNative);
        }
    }

    /**
     * The policy in force in the build.
     *
     * <p>MEASURED: the native path is not selected for this kernel, because
     * the measurement did not show it to be worth the crossing.
     */
    public static final Policy DEFAULT = Policy.javaOnly();

    /** Which path a batch is evaluated by. */
    public enum Path {
        /** The Java reference of io.matrix.bir. */
        JAVA,
        /** The C library, through FFM. */
        NATIVE
    }

    private NativeKernelPolicy() {
    }

    /**
     * Chooses the path for a batch, under the policy in force.
     *
     * @param nativeAvailable whether the shared object was loaded.
     * @param batchSize       how many rows the batch holds.
     * @param arity           the arity of the function.
     * @return the path to use.
     */
    public static Path select(boolean nativeAvailable, int batchSize, int arity) {
        return select(DEFAULT, nativeAvailable, batchSize, arity);
    }

    /**
     * Chooses the path for a batch, under a given policy.
     *
     * @param policy          the policy to apply.
     * @param nativeAvailable whether the shared object was loaded.
     * @param batchSize       how many rows the batch holds.
     * @param arity           the arity of the function.
     * @return the path to use.
     */
    public static Path select(Policy policy, boolean nativeAvailable, int batchSize, int arity) {
        if (!policy.nativeEnabled()) {
            return Path.JAVA;
        }
        if (!nativeAvailable) {
            return Path.JAVA;
        }
        if (arity < MIN_ARITY || arity > MAX_ARITY) {
            return Path.JAVA;
        }
        if (batchSize < policy.minBatchForNative()) {
            return Path.JAVA;
        }
        if (batchSize > MAX_BATCH_FOR_NATIVE) {
            return Path.JAVA;
        }
        return Path.NATIVE;
    }

    /**
     * The reason a batch is evaluated by the path it is evaluated by.
     *
     * @param policy          the policy to apply.
     * @param nativeAvailable whether the shared object was loaded.
     * @param batchSize       how many rows the batch holds.
     * @param arity           the arity of the function.
     * @return a short, human-readable reason.
     */
    public static String explain(Policy policy, boolean nativeAvailable, int batchSize, int arity) {
        if (!policy.nativeEnabled()) {
            return "the measured decision keeps this kernel on the Java path";
        }
        if (!nativeAvailable) {
            return "the native library is not available";
        }
        if (arity < MIN_ARITY || arity > MAX_ARITY) {
            return "the arity " + arity + " is outside the range the native path supports";
        }
        if (batchSize < policy.minBatchForNative()) {
            return "the batch " + batchSize + " is below the threshold "
                    + policy.minBatchForNative();
        }
        if (batchSize > MAX_BATCH_FOR_NATIVE) {
            return "the batch " + batchSize + " is above the maximum the native path accepts";
        }
        return "the batch " + batchSize + " is at or above the threshold "
                + policy.minBatchForNative();
    }
}
