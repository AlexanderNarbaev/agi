package io.matrix.bir;

import io.matrix.neuron.TruthTable;

/**
 * Adapter: wraps legacy TruthTable into BIR TtForm.
 * Per SPEC-002 FR-A4: strangler-fig migration strategy.
 * Legacy TruthTable is deprecated but works; new code should use BIR.
 */
public final class TruthTableAdapter {

    private TruthTableAdapter() {}

    /** Convert legacy TruthTable to BIR TtForm. */
    public static TtForm toBir(TruthTable tt) {
        return TtForm.fromTruthTable(tt);
    }

    /** Convert BIR TtForm back to legacy TruthTable. */
    public static TruthTable fromBir(TtForm form) {
        int k = form.k();
        java.util.BitSet bits = new java.util.BitSet(1 << k);
        long[] out = new long[1];
        for (int i = 0; i < (1 << k); i++) {
            form.eval(new long[]{i}, out);
            if (out[0] == 1) bits.set(i);
        }
        return TruthTable.of(k, bits);
    }
}
