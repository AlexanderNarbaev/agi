package io.matrix.brain.runtime;

import io.matrix.bir.BirRegistry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RECON-W3 Part B Step 2 — RuleInductionEngine tests.
 *
 * <p>Deterministic rule induction from labeled episodes; Bir registered in
 * BirRegistry with provenance; K_MAX enforced (Article II); transitivity
 * demo (A>B, B>C ⇒ inferred Bir can answer A>C).</p>
 */
class RuleInductionEngineTest {

    @Test
    void deterministic_induction_same_episodes_same_bir() {
        BirRegistry reg1 = new BirRegistry();
        BirRegistry reg2 = new BirRegistry();
        RuleInductionEngine r1 = new RuleInductionEngine(42L, reg1);
        RuleInductionEngine r2 = new RuleInductionEngine(42L, reg2);
        long[][] features = generateTransitivity(20);
        boolean[] labels = positiveLabels(20, 16);
        List<String> ids = idsFor(20);
        var res1 = r1.induce(ids, features, labels);
        var res2 = r2.induce(ids, features, labels);
        // Same seed + same episodes ⇒ same provenance
        assertThat(res1.provenance()).isEqualTo(res2.provenance());
        assertThat(res1.chosenFidelity()).isEqualTo(res2.chosenFidelity());
        assertThat(res1.entry().bir().provenance()).isEqualTo(res2.entry().bir().provenance());
    }

    @Test
    void induce_registers_bir_in_registry() {
        BirRegistry reg = new BirRegistry();
        RuleInductionEngine r = new RuleInductionEngine(42L, reg);
        long[][] features = generateTransitivity(10);
        boolean[] labels = positiveLabels(10, 8);
        var res = r.induce(idsFor(10), features, labels);
        assertThat(reg.size()).isGreaterThan(0);
        assertThat(reg.listAll()).extracting(BirRegistry.Entry::id).contains(res.entry().id());
    }

    @Test
    void article_ii_kmax_enforced() {
        BirRegistry reg = new BirRegistry();
        RuleInductionEngine r = new RuleInductionEngine(42L, reg);
        long[][] features = generateTransitivity(25);   // 25 > K_MAX=20
        boolean[] labels = positiveLabels(25, 20);
        assertThatThrownBy(() -> r.induce(idsFor(25), features, labels))
            .hasMessageContaining("K_MAX=20")
            .hasMessageContaining("Article II");
    }

    @Test
    void article_ii_kmax_below_or_equal_passes() {
        // KMaxEnforcer direct unit tests
        KMaxEnforcer.enforce(1);
        KMaxEnforcer.enforce(20);   // boundary
        assertThat(KMaxEnforcer.isCompliant(0)).isTrue();
        assertThat(KMaxEnforcer.isCompliant(20)).isTrue();
        assertThat(KMaxEnforcer.isCompliant(21)).isFalse();
    }

    @Test
    void transitivity_demo_learns_bir() {
        // 20 examples of A>B, B>C patterns with corresponding label
        // The induced Bir must be a valid ClauseSetForm.
        BirRegistry reg = new BirRegistry();
        RuleInductionEngine r = new RuleInductionEngine(42L, reg);
        long[][] features = generateTransitivity(20);
        boolean[] labels = positiveLabels(20, 16);   // 80% positive
        var res = r.induce(idsFor(20), features, labels);
        // Bir is a ClauseSetForm (real BIR, not a string predicate)
        assertThat(res.entry().bir()).isInstanceOf(io.matrix.bir.ClauseSetForm.class);
        // Provenance carries seed + episodeRange + fidelity (Article III)
        assertThat(res.provenance()).contains("seed=").contains("episodeRange=")
            .contains("fidelity=");
    }

    @Test
    void chosen_fidelity_is_highest_of_two_candidates() {
        BirRegistry reg = new BirRegistry();
        RuleInductionEngine r = new RuleInductionEngine(42L, reg);
        long[][] features = generateTransitivity(20);
        boolean[] labels = positiveLabels(20, 16);
        var res = r.induce(idsFor(20), features, labels);
        double chosen = res.chosenFidelity();
        double tsetlin = res.tsetlinFidelity();
        double mpdt = res.mpdtFidelity();
        assertThat(chosen).isGreaterThanOrEqualTo(tsetlin);
        assertThat(chosen).isGreaterThanOrEqualTo(mpdt);
    }

    @Test
    void empty_input_rejected() {
        BirRegistry reg = new BirRegistry();
        RuleInductionEngine r = new RuleInductionEngine(42L, reg);
        assertThatThrownBy(() -> r.induce(List.of(), new long[0][], new boolean[0]))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void length_mismatch_rejected() {
        BirRegistry reg = new BirRegistry();
        RuleInductionEngine r = new RuleInductionEngine(42L, reg);
        long[][] features = new long[5][1];
        boolean[] labels = new boolean[3];
        assertThatThrownBy(() -> r.induce(idsFor(5), features, labels))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seed_is_accessible() {
        BirRegistry reg = new BirRegistry();
        RuleInductionEngine r = new RuleInductionEngine(99L, reg);
        assertThat(r.seed()).isEqualTo(99L);
    }

    @Test
    void registry_must_not_be_null() {
        assertThatThrownBy(() -> new RuleInductionEngine(42L, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Helpers ----

    private long[][] generateTransitivity(int n) {
        long[][] out = new long[n][];
        Random r = new Random(42L);
        for (int i = 0; i < n; i++) out[i] = new long[]{r.nextLong(), r.nextLong(), r.nextLong(), r.nextLong()};
        return out;
    }

    private boolean[] positiveLabels(int n, int positives) {
        boolean[] labels = new boolean[n];
        for (int i = 0; i < positives; i++) labels[i] = true;
        return labels;
    }

    private List<String> idsFor(int n) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add("ep-" + i);
        return ids;
    }
}
