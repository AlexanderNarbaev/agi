package io.matrix.agent.planning;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Fluent builder for {@link Ac3Solver} — declarative constraint description
 * for plan preprocessing (DESIGN-15 §3).
 *
 * <pre>{@code
 * Ac3Solver csp = CspBuilder.vars(3).maxValue(2)
 *         .neq(0, 1)
 *         .forbidValue(2, 0)
 *         .build();
 * boolean satisfiable = csp.solve();
 * }</pre>
 */
public final class CspBuilder {

    private final int nVars;
    private final int maxValue;
    private final List<Ac3Solver.BinaryConstraint> constraints = new ArrayList<>();

    private CspBuilder(int nVars, int maxValue) {
        this.nVars = nVars;
        this.maxValue = maxValue;
    }

    public static CspBuilder vars(int nVars, int maxValue) {
        if (nVars < 1) throw new IllegalArgumentException("nVars >= 1");
        if (maxValue < 1) throw new IllegalArgumentException("maxValue >= 1");
        return new CspBuilder(nVars, maxValue);
    }

    /** Adds v_i != v_j (both directions). */
    public CspBuilder neq(int vi, int vj) {
        constraints.add(new Ac3Solver.BinaryConstraint() {
            @Override public boolean consistent(int a, int av, int b, int bv) { return av != bv; }
            @Override public int vi() { return vi; }
            @Override public int vj() { return vj; }
        });
        return this;
    }

    /** Adds vi == vj (both directions). */
    public CspBuilder eq(int vi, int vj) {
        constraints.add(new Ac3Solver.BinaryConstraint() {
            @Override public boolean consistent(int a, int av, int b, int bv) { return av == bv; }
            @Override public int vi() { return vi; }
            @Override public int vj() { return vj; }
        });
        return this;
    }

    /** Forbids a single (variable, value) assignment. */
    public CspBuilder forbidValue(int var, int val) {
        constraints.add(new Ac3Solver.BinaryConstraint() {
            @Override public boolean consistent(int a, int av, int b, int bv) {
                return !(a == var && av == val);
            }
            @Override public int vi() { return var; }
            @Override public int vj() { return var; }
        });
        return this;
    }

    /** Generic binary predicate between two distinct variables. */
    public CspBuilder constraint(int vi, int vj, BinaryPair predicate) {
        constraints.add(new Ac3Solver.BinaryConstraint() {
            @Override public boolean consistent(int a, int av, int b, int bv) {
                if (a == vi && b == vj) return predicate.holds(av, bv);
                if (a == vj && b == vi) return predicate.holds(bv, av);
                return true;
            }
            @Override public int vi() { return vi; }
            @Override public int vj() { return vj; }
        });
        return this;
    }

    /** Symmetric pair predicate. */
    public interface BinaryPair {
        boolean holds(int viVal, int vjVal);
    }

    /** Builds the solver with fresh full domains. */
    public Ac3Solver build() {
        BitSet[] domains = new BitSet[nVars];
        for (int i = 0; i < nVars; i++) {
            domains[i] = new BitSet(maxValue);
            domains[i].set(0, maxValue);
        }
        return new Ac3Solver(domains, maxValue, constraints);
    }
}
