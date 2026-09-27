package io.matrix.brain.runtime;

import io.matrix.bir.ClauseSetForm;

import java.util.ArrayList;
import java.util.List;

/**
 * RECON-W11 research iteration #3 — SymbolicSimplifier.
 *
 * <p>Symbolic algebra heuristics for clause simplification: removes
 * trivially-true clauses (empty mask), duplicates (identical pos/neg),
 * subsumed clauses (clause A subsumes B if A's pos ⊇ B's pos and
 * A's neg ⊇ B's neg).</p>
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
        // Step 3: remove subsumed (c1 subsumes c2 if pos1⊇pos2 AND neg1⊇neg2)
        List<ClauseSetForm.Clause> pass3 = new ArrayList<>(pass2);
        for (int i = 0; i < pass3.size(); i++) {
            ClauseSetForm.Clause ci = pass3.get(i);
            for (int j = 0; j < pass3.size(); j++) {
                if (i == j) continue;
                ClauseSetForm.Clause cj = pass3.get(j);
                if (subsumes(ci, cj)) { removedSub++; pass3.remove(j); j--; i--; break; }
            }
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

    private boolean subsumes(ClauseSetForm.Clause a, ClauseSetForm.Clause b) {
        if (a.pos.length != b.pos.length) return false;
        for (int i = 0; i < a.pos.length; i++) {
            if ((a.pos[i] & b.pos[i]) != b.pos[i]) return false;
            if ((a.neg[i] & b.neg[i]) != b.neg[i]) return false;
        }
        return true;
    }
}
