package io.matrix.ethics.frozen;

import io.matrix.neuron.TruthTable;

/**
 * Tiny utility for the FROZEN ethical FNL subsystem.
 *
 * <p>Centralises knowledge of the maximum {@code k} supported by {@link TruthTable}
 * so the FROZEN FNL layer doesn't have to depend on {@code TruthTable.K_MAX}
 * directly.
 */
public final class TruthTableUtil {
    private TruthTableUtil() {}

    /** Mirrors {@link TruthTable#K_MAX}. */
    public static final int MAX_K = TruthTable.K_MAX;

    /** Minimum practical width for the FROZEN FNL network. */
    public static final int MIN_K = 8;
}
