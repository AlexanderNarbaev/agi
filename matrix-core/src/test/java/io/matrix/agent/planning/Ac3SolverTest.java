package io.matrix.agent.planning;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class Ac3SolverTest {

    private Ac3Solver make(int nVars, int maxVal, List<Ac3Solver.BinaryConstraint> cs) {
        BitSet[] d = new BitSet[nVars];
        for (int i = 0; i < nVars; i++) { d[i] = new BitSet(maxVal); d[i].set(0, maxVal); }
        return new Ac3Solver(d, maxVal, cs);
    }

    @Test
    void solvesClassicXorChain() {
        // v_i != v_{i+1} chain of 3 boolean vars — satisfiable
        List<Ac3Solver.BinaryConstraint> cs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            int a = i, b = i + 1;
            cs.add(new Ac3Solver.BinaryConstraint() {
                public boolean consistent(int vi, int viV, int vj, int vjV) { return viV != vjV; }
                public int vi() { return a; } public int vj() { return b; }
            });
        }
        var s = make(3, 2, cs);
        assertThat(s.solve()).isTrue();
        // AC-3 semantics: arc consistency only — ≠-chains keep both values
        for (BitSet d : s.domains()) assertThat(d.cardinality()).isEqualTo(2);
    }

    @Test
    void detectsContradiction() {
        List<Ac3Solver.BinaryConstraint> cs = List.of(new Ac3Solver.BinaryConstraint() {
            public boolean consistent(int vi, int a, int vj, int b) { return false; } // always violated
            public int vi() { return 0; } public int vj() { return 1; }
        });
        var s = make(2, 2, cs);
        assertThat(s.solve()).isFalse();
        assertThat(s.domains()[0].isEmpty()).isTrue();
    }

    @Test
    void arcConsistencyInvariantAfterSolve() {
        Random rnd = new Random(42L);
        for (int trial = 0; trial < 20; trial++) {
        int nVars = 3 + rnd.nextInt(3), maxVal = 2 + rnd.nextInt(3);
        List<Ac3Solver.BinaryConstraint> cs = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            int a = rnd.nextInt(nVars), b = rnd.nextInt(nVars);
            if (a == b) continue;
            int forbiddenA = rnd.nextInt(maxVal), forbiddenB = rnd.nextInt(maxVal);
            cs.add(new Ac3Solver.BinaryConstraint() {
                public boolean consistent(int vi, int viV, int vj, int vjV) {
                    return !(vi == a && viV == forbiddenA && vj == b && vjV == forbiddenB);
                }
                public int vi() { return a; } public int vj() { return b; }
            });
        }
        var solver = make(nVars, maxVal, cs);
        solver.solve();
        // INVARIANT: every remaining value has at least one supporting value
        // in each constrained neighbour domain (post-solve arc consistency).
        for (Ac3Solver.BinaryConstraint c : solver.constraintsSnapshotForAudit()) {
            BitSet di = solver.domains()[c.vi()], dj = solver.domains()[c.vj()];
            for (int vi = di.nextSetBit(0); vi >= 0; vi = di.nextSetBit(vi + 1)) {
                boolean supported = false;
                for (int vj = dj.nextSetBit(0); vj >= 0 && !supported; vj = dj.nextSetBit(vj + 1)) {
                    if (c.consistent(c.vi(), vi, c.vj(), vj)) supported = true;
                }
                assertThat(supported).as("var %d val %d must keep support on var %d", c.vi(), vi, c.vj()).isTrue();
            }
        }
        }
    }
    @Test
    void cspBuilderFluentApi() {
        var csp = CspBuilder.vars(3, 2)
                .neq(0, 1)
                .eq(1, 2)
                .build();
        assertThat(csp.solve()).isTrue();
        // v0 != v1 == v2 ⇒ v0 != v2 transitively; AC-3 keeps 2 values per var here
        for (var d : csp.domains()) assertThat(d.cardinality()).isEqualTo(2);
    }

    @Test
    void cspBuilderForbidValuePrunes() {
        var csp = CspBuilder.vars(1, 4).forbidValue(0, 0).forbidValue(0, 3).build();
        assertThat(csp.solve()).isTrue();
        var d = csp.domains()[0];
        assertThat(d.get(0)).isFalse();
        assertThat(d.get(3)).isFalse();
        assertThat(d.get(1)).isTrue();
        assertThat(d.get(2)).isTrue();
    }

    @Test
    void cspBuilderContradictionDetected() {
        var csp = CspBuilder.vars(2, 2)
                .neq(0, 1)
                .forbidValue(0, 0).forbidValue(0, 1) // v0 has no values left
                .build();
        assertThat(csp.solve()).isFalse();
    }
}
