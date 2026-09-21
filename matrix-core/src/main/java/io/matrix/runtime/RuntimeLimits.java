package io.matrix.runtime;

/**
 * Runtime limits sourced from environment variables.
 *
 * <p>Wires the documented configuration knobs into a single deterministic
 * access point. Values are read lazily from {@link System#getenv} on every
 * call, so no CDI or startup ordering is required and the class stays usable
 * from Quarkus-free subsystems (BRC, MCTS, RAG).
 *
 * <p>Environment variables:
 * <ul>
 *   <li>{@code BRC_MAX_STEPS} — max reasoning steps (default 5, repo convention: BRC max 5 steps)</li>
 *   <li>{@code RAG_TOP_K} — top-K retrieval candidates (default 5)</li>
 *   <li>{@code MCTS_ITERATIONS} — MCTS/LATS search iterations (default 1000)</li>
 * </ul>
 *
 * <p>All values are clamped to a sane minimum of {@code 1}; unset, blank, or
 * non-numeric values fall back to the default. Deterministic: no randomness,
 * no wall-clock.
 */
public final class RuntimeLimits {

    /** Default BRC max steps (repo convention: BRC max 5 steps). */
    public static final int BRC_MAX_STEPS_DEFAULT = 5;

    /** Default RAG top-K candidate count. */
    public static final int RAG_TOP_K_DEFAULT = 5;

    /** Default MCTS/LATS search iteration count. */
    public static final int MCTS_ITERATIONS_DEFAULT = 1000;

    /** Environment variable name for BRC max steps. */
    public static final String BRC_MAX_STEPS_ENV = "BRC_MAX_STEPS";

    /** Environment variable name for RAG top-K. */
    public static final String RAG_TOP_K_ENV = "RAG_TOP_K";

    /** Environment variable name for MCTS iterations. */
    public static final String MCTS_ITERATIONS_ENV = "MCTS_ITERATIONS";

    private RuntimeLimits() {}

    /**
     * Returns the configured BRC max steps.
     *
     * @return max steps (≥ 1)
     */
    public static int brcMaxSteps() {
        return parsePositiveInt(System.getenv(BRC_MAX_STEPS_ENV), BRC_MAX_STEPS_DEFAULT);
    }

    /**
     * Returns the configured RAG top-K.
     *
     * @return top-K (≥ 1)
     */
    public static int ragTopK() {
        return parsePositiveInt(System.getenv(RAG_TOP_K_ENV), RAG_TOP_K_DEFAULT);
    }

    /**
     * Returns the configured MCTS/LATS iteration count.
     *
     * @return iteration count (≥ 1)
     */
    public static int mctsIterations() {
        return parsePositiveInt(System.getenv(MCTS_ITERATIONS_ENV), MCTS_ITERATIONS_DEFAULT);
    }

    /**
     * Parses a positive integer from a raw string value.
     *
     * <p>Returns {@code defaultValue} when the value is {@code null}, blank, or
     * non-numeric. The result is always clamped to a minimum of {@code 1}.
     *
     * @param raw          raw environment/string value, may be null
     * @param defaultValue fallback used when raw is absent or unparsable
     * @return the parsed value, or the default, both clamped to ≥ 1
     */
    static int parsePositiveInt(String raw, int defaultValue) {
        int fallback = Math.max(1, defaultValue);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
