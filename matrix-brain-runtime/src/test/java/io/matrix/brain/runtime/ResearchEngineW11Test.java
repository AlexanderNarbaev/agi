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
        assertThat(result.removedDuplicate()).isGreaterThanOrEqualTo(1);
        assertThat(result.outputClauses()).isEqualTo(2);  // c1 + c3
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
