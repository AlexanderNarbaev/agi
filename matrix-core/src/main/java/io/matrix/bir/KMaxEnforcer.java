package io.matrix.bir;

/**
 * RUN 174 — K_MAX enforcer (runtime guard).
 *
 * <p>Per CONSTITUTION II: "Boolean artifacts limited to K_MAX=20 inputs."
 * This class enforces K_MAX at runtime by throwing
 * IllegalArgumentException for non-compliant artifacts.
 *
 * <p>Produced by Phase δ after audit found no K_MAX violations in
 * decision-path, but enforcement is still useful for new code
 * that creates BirUnits at runtime (e.g., from distillation).
 */
public final class KMaxEnforcer {

    public static final int K_MAX = 20;
    public static final int K_MIN = 1;

    private KMaxEnforcer() {}

    /**
     * Assert that the number of inputs is within allowed range.
     * @throws IllegalArgumentException if not in [K_MIN..K_MAX]
     */
    public static void assertWithinRange(int inputs) {
        if (inputs < K_MIN || inputs > K_MAX) {
            throw new IllegalArgumentException(
                    "BirUnit inputs=" + inputs
                            + " out of range [" + K_MIN + ".." + K_MAX + "]");
        }
    }

    /** Returns true if inputs is in allowed range. */
    public static boolean isValid(int inputs) {
        return inputs >= K_MIN && inputs <= K_MAX;
    }

    /**
     * Truncate a list of inputs to K_MAX=20 boundary.
     * Returns the input size unchanged if already valid.
     */
    public static int cap(int inputs) {
        return Math.min(inputs, K_MAX);
    }
}
