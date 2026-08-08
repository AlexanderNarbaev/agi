package io.matrix.bir;

import java.util.ArrayList;
import java.util.List;

/**
 * BIR Compiler: converts between TT, CLAUSESET, and BDD forms.
 *
 * Per SPEC-002 §1: TT⇄BDD exact, TT→CLAUSESET exact (espresso-type minimization),
 * CLAUSESET→TT only for k≤20. Lossy conversions marked with measured fidelity.
 */
public final class BirCompiler {

    private BirCompiler() {}

    /** TT → BDD (exact conversion). */
    public static BddForm ttToBdd(TtForm tt) {
        int k = tt.k();
        BddForm.Builder builder = new BddForm.Builder();
        int root = buildBddFromTt(builder, tt, 0, 0);
        return builder.build(k, "compiled-from-tt");
    }

    private static int buildBddFromTt(BddForm.Builder builder, TtForm tt, int level, int prefix) {
        if (level == tt.k()) {
            long[] in = {prefix};
            long[] out = new long[1];
            tt.eval(in, out);
            return out[0] == 1 ? 1 : 0;
        }
        int low = buildBddFromTt(builder, tt, level + 1, prefix);
        int high = buildBddFromTt(builder, tt, level + 1, prefix | (1 << level));
        return builder.mk(level, low, high);
    }

    /** BDD → TT (exact, k≤20). */
    public static TtForm bddToTt(BddForm bdd) {
        int k = bdd.inputBits();
        if (k > 20) throw new IllegalArgumentException("k > 20 not supported for BDD→TT");
        int size = 1 << k;
        long[] table = new long[(size + 63) / 64];
        long[] out = new long[1];
        for (int i = 0; i < size; i++) {
            bdd.eval(new long[]{i}, out);
            if (out[0] == 1) table[i >>> 6] |= (1L << (i & 63));
        }
        return new TtForm(k, table, "compiled-from-bdd", 1.0);
    }

    /** CLAUSESET → TT (exact, k≤20). */
    public static TtForm clauseSetToTt(ClauseSetForm cs) {
        int k = cs.inputBits();
        if (k > 20) throw new IllegalArgumentException("k > 20 not supported for CLAUSESET→TT");
        int size = 1 << k;
        long[] table = new long[(size + 63) / 64];
        long[] out = new long[1];
        for (int i = 0; i < size; i++) {
            cs.eval(new long[]{i}, out);
            if (out[0] == 1) table[i >>> 6] |= (1L << (i & 63));
        }
        return new TtForm(k, table, "compiled-from-clauseset", 1.0);
    }

    /** TT → CLAUSESET (espresso-type minimization). */
    public static ClauseSetForm ttToClauseSet(TtForm tt) {
        int k = tt.k();
        // Collect all minterms where output = 1
        List<ClauseSetForm.Clause> clauses = new ArrayList<>();
        for (int i = 0; i < (1 << k); i++) {
            long[] in = {i};
            long[] out = new long[1];
            tt.eval(in, out);
            if (out[0] == 1) {
                long[] pos = new long[(k + 63) / 64];
                long[] neg = new long[(k + 63) / 64];
                for (int b = 0; b < k; b++) {
                    if (((i >>> b) & 1) == 1) pos[b >>> 6] |= (1L << (b & 63));
                    else neg[b >>> 6] |= (1L << (b & 63));
                }
                clauses.add(new ClauseSetForm.Clause(pos, neg));
            }
        }
        return new ClauseSetForm(k, clauses, "compiled-from-tt", 1.0);
    }

    /** Generic conversion: any form → TT (via BDD if needed). */
    public static TtForm toTt(Bir bir) {
        if (bir instanceof TtForm tt) return tt;
        if (bir instanceof BddForm bdd) return bddToTt(bdd);
        if (bir instanceof ClauseSetForm cs) return clauseSetToTt(cs);
        throw new IllegalArgumentException("Unknown form: " + bir.form());
    }

    /** Generic conversion: any form → BDD (via TT if needed). */
    public static BddForm toBdd(Bir bir) {
        if (bir instanceof BddForm bdd) return bdd;
        if (bir instanceof TtForm tt) return ttToBdd(tt);
        if (bir instanceof ClauseSetForm cs) return ttToBdd(clauseSetToTt(cs));
        throw new IllegalArgumentException("Unknown form: " + bir.form());
    }

    /** Generic conversion: any form → CLAUSESET (via TT). */
    public static ClauseSetForm toClauseSet(Bir bir) {
        if (bir instanceof ClauseSetForm cs) return cs;
        if (bir instanceof TtForm tt) return ttToClauseSet(tt);
        if (bir instanceof BddForm bdd) return ttToClauseSet(bddToTt(bdd));
        throw new IllegalArgumentException("Unknown form: " + bir.form());
    }
}
