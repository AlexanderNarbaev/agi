package io.matrix.knowledge.concept.research;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — the referent policy: a prepositional phrase is not a referent.
 *
 * <p><b>What this is.</b> The probe of a defect the owner's <b>migrated</b> live store
 * exposed: two of the store's 22 referents are the phrases <i>"In the assignment under
 * consideration"</i> and <i>"In the other assignment under consideration"</i> — a
 * prepositional phrase promoted to a concept, with ten claims pointing at it.
 *
 * <p><b>Why the policy missed it.</b> The policy cuts a span that opens with a sentence
 * modifier only when the span carries a comma (an adverbial plus a subject). A span that
 * opens with a preposition and carries <b>no</b> comma was never tested: a subordinator
 * opener is rejected ({@code CLAUSE_SUBORDINATOR}) and a preposition opener was not.
 *
 * <p><b>What it pins.</b> The defect: a span that opens with a preposition and has no
 * comma is a <b>modifier</b>, not a noun phrase, so it cannot name a thing.</p>
 */
@DisplayName("P1 — the referent policy: a prepositional phrase is not a referent")
class PrepositionalPhraseProbeTest {

    @Test
    @DisplayName("a span that opens with a preposition and has no comma is not a referent")
    void aPrepositionalPhraseIsNotAReferent() {
        // The two surfaces the migrated live store promoted.
        assertThat(ReferentPolicy.rejection("In the assignment under consideration"))
                .as("the assignment under consideration").isNotEqualTo(ReferentPolicy.Rejection.NONE);
        assertThat(ReferentPolicy.rejection("In the other assignment under consideration"))
                .as("the other assignment under consideration")
                .isNotEqualTo(ReferentPolicy.Rejection.NONE);
    }

    @Test
    @DisplayName("the control: a preposition INSIDE a noun phrase is still a referent")
    void aPrepositionInsideANounPhraseIsStillAReferent() {
        // The complement case: the head is the noun BEFORE the preposition, so the
        // phrase names a thing and must stay accepted.
        assertThat(ReferentPolicy.rejection("The theory of plate tectonics"))
                .as("the theory of plate tectonics")
                .isEqualTo(ReferentPolicy.Rejection.NONE);
        assertThat(ReferentPolicy.rejection("The Calvin-Benson cycle in oxygenic phototrophs"))
                .as("the Calvin-Benson cycle in oxygenic phototrophs")
                .isEqualTo(ReferentPolicy.Rejection.NONE);
    }
}
