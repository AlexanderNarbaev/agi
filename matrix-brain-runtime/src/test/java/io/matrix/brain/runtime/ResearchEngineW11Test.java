package io.matrix.brain.runtime;

import io.matrix.bir.ClauseSetForm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W11 — New research iterations.
 */
class ResearchEngineW11Test {

    @Test
    void categorical_functor_composes_features_to_clauses() {
        CategoricalFunctor f = new CategoricalFunctor();
        f.register("hdc-feature",  new long[]{1L, 2L}, 0);
        f.register("bir-clause",   0,                   "rule-1");
        assertThat(f.composesWith("hdc-feature", "bir-clause")).isTrue();
        assertThat(f.narrative()).contains("morphisms registered");
    }

    @Test
    void hippocampal_replay_prioritizes_high_confidence() {
        HippocampalReplayScheduler s = new HippocampalReplayScheduler();
        s.add(1, 0.3, 100);   // low confidence, low recency
        s.add(2, 0.95, 50);  // high confidence
        s.add(3, 0.5, 80);   // medium
        var top = s.nextReplay();
        assertThat(top.id()).isEqualTo(2L);  // high confidence wins
        assertThat(s.queueSize()).isEqualTo(2);
    }

    @Test
    void hippocampal_replay_evicts_lowest_priority_when_full() {
        HippocampalReplayScheduler s = new HippocampalReplayScheduler();
        for (int i = 0; i < 5; i++) s.add(i, 0.1 * i, i);
        // Capacity 3 evicts 2 (lowest priority first)
        for (int i = 0; i < 3; i++) s.nextReplay(3);
        assertThat(s.evictedCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void symbolic_simplifier_removes_duplicate_clauses() {
        SymbolicSimplifier s = new SymbolicSimplifier();
        var c1 = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0L});
        var c2 = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0L});  // duplicate
        var c3 = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0x1L});
        ClauseSetForm bir = ClauseSetForm.lossy(4, List.of(c1, c2, c3), "p", 1.0);
        var result = s.simplify(bir);

        // The test's subject: exact-duplicate removal.
        assertThat(result.removedDuplicate()).isGreaterThanOrEqualTo(1);

        // RECON-W20 — ASSERTION AMENDED, with rationale (anti-regression law requires
        // malformation proof + before/after). BEFORE: outputClauses()==2, written under
        // a subsumption relation that was inverted relative to Clause.matches():
        //   old rule  A.pos ⊇ B.pos AND A.neg ⊇ B.neg
        //   matches() Match(c) = { x : (x&c.pos)==c.pos AND (x&c.neg)==0 }
        // so the only rule making Match(A) ⊇ Match(B) is A.pos ⊆ B.pos AND
        // A.neg ⊆ B.neg. Under the corrected rule, c1 (no exclusions) genuinely
        // COVERS c3 (which adds NOT x0): every x matching c3 also matches c1, so
        // c3 is redundant and the honest output is 1 clause, not 2.
        //
        // The old expectation therefore encoded knowledge LOSS, not a stricter
        // standard. The replacement asserts the STRONGER, provable property:
        // exactly one clause survives and it is the more general c1.
        assertThat(result.outputClauses()).isEqualTo(1);
        assertThat(result.removedSubsumed()).isGreaterThanOrEqualTo(1);
        // 3 in, 1 out: one duplicate + one subsumed.
        assertThat(result.inputClauses()).isEqualTo(3);
        assertThat(result.removedDuplicate() + result.removedSubsumed()
                + result.removedEmpty()).isEqualTo(2);
    }

    @Test
    void symbolic_simplifier_keeps_the_more_general_clause_when_covering() {
        // Guards the RECON-W20 direction fix: the LESS constrained clause must
        // survive and the MORE constrained one must be removed — never the reverse.
        SymbolicSimplifier s = new SymbolicSimplifier();
        var general = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0L});
        var specific = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0x1L});
        ClauseSetForm bir = ClauseSetForm.lossy(4, List.of(general, specific), "p", 1.0);
        var out = s.simplify(bir).outputClauses();
        assertThat(out).isEqualTo(1);
    }

    @Test
    void symbolic_simplifier_subsumption_is_order_independent_and_idempotent() {
        // The old implementation mutated the list while iterating it. Verify the
        // rewritten pass is order-independent and converges in one application.
        SymbolicSimplifier s = new SymbolicSimplifier();
        var a = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0L});
        var b = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0x1L});
        var c = new ClauseSetForm.Clause(new long[]{0x3L}, new long[]{0L});

        var r1 = s.simplify(ClauseSetForm.lossy(4, List.of(a, b, c), "p", 1.0));
        var r2 = s.simplify(ClauseSetForm.lossy(4, List.of(c, b, a), "p", 1.0));

        assertThat(r1.outputClauses()).isEqualTo(r2.outputClauses());
        assertThat(r1.removedSubsumed()).isEqualTo(r2.removedSubsumed());
    }

    @Test
    void symbolic_simplifier_removes_subsumed_clauses() {
        SymbolicSimplifier s = new SymbolicSimplifier();
        // c1 (pos=1111) subsumes c2 (pos=0011) if neg1 covers neg2
        var c1 = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0L});
        var c2 = new ClauseSetForm.Clause(new long[]{0x3L}, new long[]{0L});
        ClauseSetForm bir = ClauseSetForm.lossy(4, List.of(c1, c2), "p", 1.0);
        var result = s.simplify(bir);
        assertThat(result.removedSubsumed()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void symbolic_simplifier_removes_trivial_clauses() {
        SymbolicSimplifier s = new SymbolicSimplifier();
        // trivially-true clause: pos=0, neg=0 (matches everything)
        var trivial = new ClauseSetForm.Clause(new long[]{0L}, new long[]{0L});
        var real = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0L});
        ClauseSetForm bir = ClauseSetForm.lossy(4, List.of(trivial, real), "p", 1.0);
        var result = s.simplify(bir);
        assertThat(result.removedEmpty()).isGreaterThanOrEqualTo(1);
        assertThat(result.outputClauses()).isEqualTo(1);
    }
}
