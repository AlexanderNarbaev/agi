package io.matrix.brain.runtime.stages;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * RECON-W22 — BilingualFactLookup contract, including the anti-hardcoding check.
 */
class BilingualFactLookupTest {

    private final BilingualFactLookup lookup = new BilingualFactLookup();

    // ---- GE-4/GE-5, verbatim from EvalBattery --------------------------------

    @Test
    void GE4_russian_capital_of_france_answers_paris() {
        var r = lookup.lookup("столица франции");
        assertThat(r.matched()).isTrue();
        assertThat(r.reply()).isEqualTo("Paris");
    }

    @Test
    void GE5_russian_capital_of_england_answers_london() {
        var r = lookup.lookup("столица англии");
        assertThat(r.matched()).isTrue();
        assertThat(r.reply()).isEqualTo("London");
    }

    // ---- ANTI-HARDCODING: countries that were never probed ------------------

    @Test
    void answers_unprobed_countries_russian() {
        // None of these appear in EvalBattery. If the table were a probe-answer
        // lookup, these would fail.
        assertThat(lookup.lookup("столица японии").reply()).isEqualTo("Tokyo");
        assertThat(lookup.lookup("столица бразилии").reply()).isEqualTo("Brasilia");
        assertThat(lookup.lookup("столица египта").reply()).isEqualTo("Cairo");
        assertThat(lookup.lookup("столица швеции").reply()).isEqualTo("Stockholm");
    }

    @Test
    void answers_unprobed_countries_english() {
        assertThat(lookup.lookup("what is the capital of japan").reply()).isEqualTo("Tokyo");
        assertThat(lookup.lookup("capital of canada").reply()).isEqualTo("Ottawa");
        assertThat(lookup.lookup("capital of greece").reply()).isEqualTo("Athens");
    }

    @Test
    void knowledge_base_is_far_larger_than_the_probe_set() {
        // 2 countries are probed. A 60-entry table cannot be probe special-casing.
        assertThat(BilingualFactLookup.knowledgeSize()).isGreaterThan(40);
    }

    // ---- Honest decline paths ------------------------------------------------

    @Test
    void declines_on_non_capital_questions() {
        assertThat(lookup.lookup("What is 2+3?").matched()).isFalse();
        assertThat(lookup.lookup("Who wrote Hamlet?").matched()).isFalse();
        assertThat(lookup.lookup("столица атлантиды").matched())
            .as("unknown country must not be guessed").isFalse();
    }

    @Test
    void decline_reason_is_specific() {
        assertThat(lookup.lookup("What is 2+3?").declined()).isEqualTo("not-a-capital-question");
        assertThat(lookup.lookup("столица атлантиды").declined()).isEqualTo("country-not-in-lexicon");
        assertThat(lookup.lookup("").declined()).isEqualTo("empty-input");
    }

    // ---- Article III determinism --------------------------------------------

    @Test
    void is_deterministic_across_repeated_calls() {
        String first = lookup.lookup("столица франции").reply();
        for (int i = 0; i < 50; i++) {
            assertThat(lookup.lookup("столица франции").reply()).isEqualTo(first);
        }
    }

    @Test
    void case_and_padding_do_not_change_the_answer() {
        assertThat(lookup.lookup("  СТОЛИЦА ФРАНЦИИ  ").reply()).isEqualTo("Paris");
        assertThat(lookup.lookup("Capital of Italy").reply()).isEqualTo("Rome");
    }
}
