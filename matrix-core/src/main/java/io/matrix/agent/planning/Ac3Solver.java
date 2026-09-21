package io.matrix.agent.planning;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.BitSet;
import java.util.List;

/**
 * AC-3 arc-consistency solver over a finite binary CSP (Mackworth 1977;
 * atlas §101) — preprocessing stage for {@code ExecutablePlanner} so that
 * plans are only generated over pruned, consistent variable domains
 * (boolean-machine vision: constraint propagation is boolean-native and
 * fully deterministic).
 *
 * <p>Model: variables are indices {@code 0..nVars-1}; each domain is a
 * {@link BitSet} of values {@code 0..maxValue-1}; binary constraints are
 * predicates {@code (vi, vi_val, vj, vj_val)} supplied pairwise. Domains are
 * modified IN PLACE by {@link #solve()} (values without support are removed);
 * the solver reports whether any domain was emptied (= unsatisfiable CSP).
 *
 * <p>Deterministic: ACED queue seeded with all arcs, processed FIFO; no
 * randomness, no wall-clock — safe for the runtime contour.
 */
public final class Ac3Solver {

    /** Binary constraint between two distinct variables (implemented as anonymous class or lambda via a single-target adapter). */
    public interface BinaryConstraint {
        /** @return true iff (vi=viVal, vj=vjVal) is consistent. */
        boolean consistent(int vi, int viVal, int vj, int vjVal);

        int vi();
        int vj();
    }

    private final BitSet[] domains;
    private final int maxValue;
    private final List<BinaryConstraint> constraints;

    public Ac3Solver(BitSet[] domains, int maxValue, List<BinaryConstraint> constraints) {
        this.domains = domains;
        this.maxValue = maxValue;
        this.constraints = List.copyOf(constraints);
    }

    /**
     * Runs AC-3 until fixpoint.
     *
     * @return false iff some domain became empty (CSP unsatisfiable)
     */
    public boolean solve() {
        // Unary constraints (self-loops vi==vj) are filters, not arcs —
        // treating them as arcs lets the reverse-direction check falsely
        // "support" forbidden values (classic AC-3 self-loop pitfall).
        List<BinaryConstraint> binary = new ArrayList<>();
        for (BinaryConstraint c : constraints) {
            if (c.vi() == c.vj()) {
                for (int v = domains[c.vi()].nextSetBit(0); v >= 0 && v < maxValue;
                     v = domains[c.vi()].nextSetBit(v + 1)) {
                    if (!c.consistent(c.vi(), v, c.vj(), v)) domains[c.vi()].clear(v);
                }
            } else {
                binary.add(c);
            }
        }
        Deque<int[]> queue = new ArrayDeque<>();
        for (BinaryConstraint c : binary) {
            queue.add(new int[]{c.vi(), c.vj()});
            queue.add(new int[]{c.vj(), c.vi()});
        }
        List<BinaryConstraint> constraintsRef = binary;
        while (!queue.isEmpty()) {
            int[] arc = queue.poll();
            if (!revise(arc[0], arc[1], constraintsRef)) continue;
            if (domains[arc[0]].isEmpty()) return false;
            for (BinaryConstraint c : constraintsRef) {
                if (c.vj() == arc[0] && c.vi() != arc[1]) queue.add(new int[]{c.vi(), c.vj()});
                else if (c.vi() == arc[0] && c.vj() != arc[1]) queue.add(new int[]{c.vj(), c.vi()});
            }
        }
        return true;
    }

    /** Removes unsupported values of xi w.r.t. xj. @return true if revised */
    private boolean revise(int xi, int xj, List<BinaryConstraint> constraints) {
        boolean revised = false;
        for (int vi = domains[xi].nextSetBit(0); vi >= 0 && vi < maxValue; vi = domains[xi].nextSetBit(vi + 1)) {
            boolean supported = false;
            for (int vj = domains[xj].nextSetBit(0); vj >= 0 && vj < maxValue && !supported; vj = domains[xj].nextSetBit(vj + 1)) {
                for (BinaryConstraint c : constraints) {
                    if ((c.vi() == xi && c.vj() == xj && c.consistent(xi, vi, xj, vj))
                            || (c.vi() == xj && c.vj() == xi && c.consistent(xj, vj, xi, vi))) {
                        supported = true;
                        break;
                    }
                }
            }
            if (!supported) { domains[xi].clear(vi); revised = true; }
        }
        return revised;
    }

    public BitSet[] domains() { return domains; }

    /** Read-only view for invariant audits/tests. */
    public List<BinaryConstraint> constraintsSnapshotForAudit() { return constraints; }
    public int varCount() { return domains.length; }
}
