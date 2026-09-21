package io.matrix.bir;

import java.util.ArrayList;
import java.util.List;

import io.matrix.compression.TruthTableMinimizer;

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
        return builder.build(k, "compiled-from-tt", root);
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

    /**
     * TT → CLAUSESET (espresso-type minimization, exact).
     *
     * <p>Uses {@link TruthTableMinimizer} (Quine-McCluskey for k ≤ 12,
     * Espresso heuristic for k &gt; 12) to produce a minimized DNF, then maps
     * each implicant to a clause: pos = set bits, neg = cleared bits,
     * don't-care positions omitted.
     *
     * <p>Exactness (SPEC-002 §1): the QM path is exact by construction. The
     * Espresso path samples minterms for k &gt; 12, so any 1-minterm left
     * uncovered by the minimized DNF is restored as a full-minterm clause —
     * eval(CLAUSESET) = eval(TT) always holds.
     */
    public static ClauseSetForm ttToClauseSet(TtForm tt) {
        int k = tt.k();
        TruthTableMinimizer.MinimizedDNF dnf =
                TruthTableMinimizer.minimize(TruthTableAdapter.fromBir(tt));
        long bitMask = (1L << k) - 1;
        List<ClauseSetForm.Clause> clauses = new ArrayList<>();
        for (TruthTableMinimizer.Implicant imp : dnf.implicants()) {
            long pos = imp.bits() & ~imp.dontCare() & bitMask;
            long neg = ~imp.bits() & ~imp.dontCare() & bitMask;
            clauses.add(new ClauseSetForm.Clause(new long[]{pos}, new long[]{neg}));
        }
        if (dnf.algorithm() == TruthTableMinimizer.Algorithm.ESPRESSO) {
            // Restore exactness: add uncovered 1-minterms as full clauses.
            long[] in = new long[1];
            long[] out = new long[1];
            for (int i = 0; i < (1 << k); i++) {
                in[0] = i;
                tt.eval(in, out);
                if (out[0] == 1 && !dnf.evaluate(i)) {
                    long pos = i & bitMask;
                    long neg = ~i & bitMask;
                    clauses.add(new ClauseSetForm.Clause(new long[]{pos}, new long[]{neg}));
                }
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
