package io.matrix.brain.runtime;

/**
 * RECON-W3 Part B — Article II guard for K_MAX=20 inputs per boolean artifact.
 *
 * <p>Enforced on every RuleInductionEngine.induce() call and validated
 * by KMaxEnforcerTest.</p>
 */
public final class KMaxEnforcer {
    public static final int K_MAX = 20;

    private KMaxEnforcer() {}

    /** Throws if the number of input clauses / episodes exceeds K_MAX=20. */
    public static void enforce(int inputCount) {
        if (inputCount > K_MAX) {
            throw new IllegalStateException(
                "Article II: K_MAX=" + K_MAX + " violated (inputCount=" + inputCount + ")");
        }
    }

    /** True iff inputCount is within Article II bounds. */
    public static boolean isCompliant(int inputCount) {
        return inputCount <= K_MAX;
    }
}
