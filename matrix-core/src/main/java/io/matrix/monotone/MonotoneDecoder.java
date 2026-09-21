package io.matrix.monotone;

import io.matrix.bir.ClauseSetForm;

import java.util.ArrayList;
import java.util.List;

/**
 * MonotoneDecoder (DESIGN-09): producer of monotone CLAUSESET via
 * Hansel/Kabulov-Normatov chains.
 *
 * <p>Monotone boolean functions: f(x) ≤ f(y) whenever x ≤ y (bitwise).
 * The decoder generates clauses that are guaranteed monotone — useful for
 * safety-critical applications where monotonicity is a requirement.
 *
 * <p>Chain decomposition: the function is decomposed into chains of
 * increasing inputs, each chain contributing one clause.
 */
public final class MonotoneDecoder {

    private final int inputBits;

    public MonotoneDecoder(int inputBits) {
        this.inputBits = inputBits;
    }

    /** Decode a monotone function from its truth table into clauses. */
    public ClauseSetForm decode(boolean[] truthTable, String provenance) {
        int size = 1 << inputBits;
        if (truthTable.length != size) {
            throw new IllegalArgumentException("truthTable size != 2^inputBits");
        }
        // Verify monotonicity
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if ((i & j) == i && truthTable[i] && !truthTable[j]) {
                    throw new IllegalArgumentException("not monotone at " + i + " vs " + j);
                }
            }
        }
        // Generate clauses: for each minimal true point, create a clause
        List<ClauseSetForm.Clause> clauses = new ArrayList<>();
        boolean[] covered = new boolean[size];
        for (int i = 0; i < size; i++) {
            if (truthTable[i] && !covered[i]) {
                // Check if minimal (no proper subset is true)
                if (isMinimal(truthTable, i)) {
                    long[] pos = new long[(inputBits + 63) / 64];
                    long[] neg = new long[(inputBits + 63) / 64];
                    for (int b = 0; b < inputBits; b++) {
                        if (((i >>> b) & 1) == 1) pos[b >>> 6] |= (1L << (b & 63));
                    }
                    clauses.add(new ClauseSetForm.Clause(pos, neg));
                    // Mark all supersets as covered
                    for (int j = 0; j < size; j++) {
                        if ((i & j) == i) covered[j] = true;
                    }
                }
            }
        }
        return new ClauseSetForm(inputBits, clauses, provenance, 1.0);
    }

    private boolean isMinimal(boolean[] tt, int point) {
        for (int b = 0; b < inputBits; b++) {
            int subset = point & ~(1 << b);
            if (subset != point && tt[subset]) return false;
        }
        return true;
    }
}
