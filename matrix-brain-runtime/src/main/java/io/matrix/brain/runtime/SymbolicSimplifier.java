package io.matrix.brain.runtime;

import io.matrix.bir.ClauseSetForm;

import java.util.ArrayList;
import java.util.List;

/**
 * RECON-W11 research iteration #3 — SymbolicSimplifier.
 *
 * <p>Symbolic algebra heuristics for clause simplification: removes
 * trivially-true clauses (empty mask), duplicates (identical pos/neg),
 * subsumed clauses (RECON-W20: A subsumes B iff A.pos ⊆ B.pos and
 * A's neg ⊆ B's neg, derived from Clause.matches()).</p>
 */
public final class SymbolicSimplifier {

    public record SimplificationResult(
        int inputClauses,
        int outputClauses,
        int removedEmpty,
        int removedDuplicate,
        int removedSubsumed
    ) {}

    public SimplificationResult simplify(ClauseSetForm bir) {
        if (bir == null || bir.clauses().isEmpty()) {
            return new SimplificationResult(0, 0, 0, 0, 0);
        }
        int input = bir.clauses().size();
        int removedEmpty = 0, removedDup = 0, removedSub = 0;
        // Step 1: remove trivially-true (neg == 0, pos == 0) — covered by ALL inputs
        List<ClauseSetForm.Clause> pass1 = new ArrayList<>();
        for (ClauseSetForm.Clause c : bir.clauses()) {
            boolean allZero = true;
            for (int i = 0; i < c.pos.length; i++) {
                if (c.pos[i] != 0L || c.neg[i] != 0L) { allZero = false; break; }
            }
            if (allZero) removedEmpty++;
            else pass1.add(c);
        }
        // Step 2: remove duplicates (same pos/neg)
        List<ClauseSetForm.Clause> pass2 = new ArrayList<>();
        for (ClauseSetForm.Clause c : pass1) {
            boolean dup = false;
            for (ClauseSetForm.Clause e : pass2) {
                if (sameMask(c.pos, e.pos) && sameMask(c.neg, e.neg)) {
                    dup = true; break;
                }
            }
            if (dup) removedDup++;
            else pass2.add(c);
        }
        // Step 3: remove subsumed clauses.
        //
        // RECON-W20 BUGFIX. BEFORE: the rule was "A.pos ⊇ B.pos AND A.neg ⊇ B.neg",
        // with an inner index-mutating delete loop. That relation is inverted with
        // respect to Clause.matches(), whose match set is
        //     Match(c) = { x : (x & c.pos) == c.pos  AND  (x & c.neg) == 0 }.
        // For Match(A) to CONTAIN Match(B) we need A's positive requirements to be
        // no stronger and A's exclusions to be no broader, i.e.
        //     A.pos ⊆ B.pos   AND   A.neg ⊆ B.neg.
        // The old rule let a MORE constrained clause delete a LESS constrained one
        // (c3 = {pos 1111, NOT x0} wrongly deleted c1 = {pos 1111}), which is the
        // exact opposite of minimisation and loses knowledge mass.
        //
        // AFTER: the predicate is the provable Match-inclusion relation, and the
        // pass is rewritten as a single "keep iff not strictly covered" filter,
        // which is order-independent and idempotent (no index mutation while
        // iterating).
        List<ClauseSetForm.Clause> pass3 = new ArrayList<>();
        for (int i = 0; i < pass2.size(); i++) {
            ClauseSetForm.Clause ci = pass2.get(i);
            boolean covered = false;
            for (int j = 0; j < pass2.size(); j++) {
                if (i == j) continue;
                if (subsumes(pass2.get(j), ci)) { covered = true; break; }
            }
            if (covered) removedSub++;
            else pass3.add(ci);
        }
        int output = pass3.size();
        return new SimplificationResult(input, output,
            removedEmpty, removedDup, removedSub);
    }

    private boolean sameMask(long[] a, long[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    /**
     * RECON-W20 — corrected subsumption relation.
     *
     * <p>Returns true when every input matched by {@code b} is also matched by
     * {@code a} (Match(a) ⊇ Match(b)), i.e. {@code b} is redundant given
     * {@code a}. Derived directly from {@code Clause.matches}: A covers B iff
     * A requires no more bits set ({@code a.pos ⊆ b.pos}) and forbids no fewer
     * bits ({@code a.neg ⊆ b.neg}).</p>
     */
    private boolean subsumes(ClauseSetForm.Clause a, ClauseSetForm.Clause b) {
        if (a.pos.length != b.pos.length) return false;
        for (int i = 0; i < a.pos.length; i++) {
            // a's required bits must be a subset of b's required bits.
            if ((a.pos[i] & ~b.pos[i]) != 0L) return false;
            // a's forbidden bits must be a subset of b's forbidden bits.
            if ((a.neg[i] & ~b.neg[i]) != 0L) return false;
        }
        return true;
    }
}
