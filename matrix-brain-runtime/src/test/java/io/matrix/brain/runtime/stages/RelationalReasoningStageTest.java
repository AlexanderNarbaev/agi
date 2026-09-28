package io.matrix.brain.runtime.stages;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * RECON-W22 — RelationalReasoningStage contract.
 *
 * <p>The GE-* probe texts are <b>frozen benchmark contracts</b> (anti-regression law:
 * probes may not be changed to inflate scores). They are reproduced here verbatim
 * from {@code EvalBattery} so this test pins the exact live behaviour.</p>
 */
class RelationalReasoningStageTest {

    private final RelationalReasoningStage stage = new RelationalReasoningStage();

    // ---- GE-1/2/3: transitivity, verbatim from EvalBattery -------------------

    @Test
    void GE1_transitivity_tall_chain_answers_carol() {
        var r = stage.tryEvaluate("Alice taller than Bob, Bob taller than Carol. Who is shortest?");
        assertThat(r.matched()).isTrue();
        assertThat(r.reply()).isEqualTo("carol");
        assertThat(r.rule()).isEqualTo("transitivity");
    }

    @Test
    void GE2_transitivity_old_chain_answers_z() {
        var r = stage.tryEvaluate("X older than Y, Y older than Z. Who is youngest?");
        assertThat(r.matched()).isTrue();
        assertThat(r.reply()).isEqualTo("z");
    }

    @Test
    void GE3_transitivity_fast_chain_answers_c() {
        var r = stage.tryEvaluate("A faster than B, B faster than C. Who is slowest?");
        assertThat(r.matched()).isTrue();
        assertThat(r.reply()).isEqualTo("c");
    }

    // ---- GE-7: unanimous attribute, verbatim --------------------------------

    @Test
    void GE7_unanimous_attribute_answers_small() {
        var r = stage.tryEvaluate("puppy small; kitten small; cub small. foal is ?");
        assertThat(r.matched()).isTrue();
        assertThat(r.reply()).isEqualTo("small");
        assertThat(r.rule()).isEqualTo("unanimous-attribute");
    }

    // ---- GE-6 must DECLINE, not guess ---------------------------------------

    @Test
    void GE6_disagreeing_attributes_decline_with_a_reason() {
        var r = stage.tryEvaluate("tomato is red; carrot is orange; banana is yellow. lemon is ?");
        // Article VIII: a miss must state why, and must NOT invent an answer.
        assertThat(r.matched()).isFalse();
        assertThat(r.reply()).isEmpty();
        assertThat(r.declined()).contains("disagree");
    }

    // ---- Generalisation beyond the frozen probes (anti-hardcoding evidence) ---

    @Test
    void generalises_to_unseen_entities_and_adjectives() {
        // Different names, different comparative, not in EvalBattery.
        var r = stage.tryEvaluate("Zara richer than Yara, Yara richer than Wanda. Who is poorest?");
        assertThat(r.matched()).isTrue();
        assertThat(r.reply()).isEqualTo("wanda");
    }

    @Test
    void top_marker_returns_chain_head() {
        var r = stage.tryEvaluate("Ann older than Ben, Ben older than Cid. Who is oldest?");
        assertThat(r.matched()).isTrue();
        assertThat(r.reply()).isEqualTo("ann");
    }

    @Test
    void three_link_chain_resolves() {
        var r = stage.tryEvaluate("P taller than Q, Q taller than R, R taller than S. Who is shortest?");
        assertThat(r.matched()).isTrue();
        assertThat(r.reply()).isEqualTo("s");
    }

    // ---- Honest decline paths ------------------------------------------------

    @Test
    void declines_on_single_edge_chain() {
        var r = stage.tryEvaluate("Alice taller than Bob. Who is shortest?");
        assertThat(r.matched()).isFalse();
        assertThat(r.declined()).contains("shorter-than-2-edges");
    }

    @Test
    void declines_when_no_superlative_is_asked() {
        var r = stage.tryEvaluate("Alice taller than Bob, Bob taller than Carol.");
        assertThat(r.matched()).isFalse();
        assertThat(r.declined()).contains("no-superlative-marker");
    }

    @Test
    void declines_on_empty_and_null_input() {
        assertThat(stage.tryEvaluate(null).matched()).isFalse();
        assertThat(stage.tryEvaluate("").matched()).isFalse();
        assertThat(stage.tryEvaluate("   ").matched()).isFalse();
    }

    @Test
    void declines_on_unrelated_text_rather_than_inventing() {
        // Arithmetic and plain questions must fall through untouched.
        for (String s : new String[]{"What is 2+3?", "hello world",
                                     "Who is the president?", "столица франции"}) {
            assertThat(stage.tryEvaluate(s).matched())
                .as("must not claim unrelated input: %s", s).isFalse();
        }
    }

    // ---- Article III determinism --------------------------------------------

    @Test
    void is_deterministic_across_repeated_calls() {
        String q = "Alice taller than Bob, Bob taller than Carol. Who is shortest?";
        String first = stage.tryEvaluate(q).reply();
        for (int i = 0; i < 25; i++) {
            assertThat(stage.tryEvaluate(q).reply()).isEqualTo(first);
        }
    }

    @Test
    void confidence_is_bounded_and_nonzero_on_match() {
        var r = stage.tryEvaluate("Alice taller than Bob, Bob taller than Carol. Who is shortest?");
        assertThat(r.confidence()).isBetween(0.0, 1.0).isGreaterThan(0.0);
    }
}
