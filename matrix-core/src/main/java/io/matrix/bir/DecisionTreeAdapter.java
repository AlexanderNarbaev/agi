package io.matrix.bir;

import io.matrix.neuron.DecisionTree;

/**
 * Adapter: wraps legacy DecisionTree into BIR TtForm.
 * DecisionTree evaluates via tree traversal; TtForm evaluates via table lookup.
 * Both produce the same boolean output for the same input.
 */
public final class DecisionTreeAdapter {

    private DecisionTreeAdapter() {}

    /** Convert legacy DecisionTree to BIR TtForm (by exhaustive evaluation). */
    public static TtForm toBir(DecisionTree dt, int k) {
        if (k < 1 || k > 20) throw new IllegalArgumentException("k in 1..20");
        int size = 1 << k;
        long[] table = new long[(size + 63) / 64];
        for (int i = 0; i < size; i++) {
            if (dt.evaluate(java.util.BitSet.valueOf(new long[]{i}))) table[i >>> 6] |= (1L << (i & 63));
        }
        return new TtForm(k, table, "legacy-decisiontree", 1.0);
    }
}
