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
 *
 * <p><b>Why the reason is asserted, not just "not accepted".</b> A span can fail for
 * several unrelated reasons (a function head, a clause cut, a bare plural). An assertion
 * of the form "not {@link ReferentPolicy.Rejection#NONE}" would still hold if the rule
 * that rejects the span changed — and the published claim is about <i>this</i> rule. The
 * defect surfaces therefore assert the <b>specific</b> reason
 * {@link ReferentPolicy.Rejection#PREPOSITIONAL_MODIFIER}.</p>
 *
 * <p><b>Why the third control is here.</b> The report and OD-071 publish the control
 * "In vitro photosynthesis" — a preposition followed by a <b>noun</b> must stay a referent.
 * It was named in the policy's own javadoc and asserted nowhere, so a widening of the rule
 * would have gone unnoticed. It is asserted here, and the rule's narrowness is measured:
 * the preposition must be followed by a <b>determiner</b> ("In <i>the</i> assignment" is
 * a modifier; "In <i>vitro</i> photosynthesis" names a thing).</p>
 */
@DisplayName("P1 — the referent policy: a prepositional phrase is not a referent")
class PrepositionalPhraseProbeTest {

    @Test
    @DisplayName("a span that opens with a preposition and has no comma is not a referent")
    void aPrepositionalPhraseIsNotAReferent() {
        // The two surfaces the migrated live store promoted. The REASON is asserted, so
        // that a different rule cannot silently take over the rejection.
        assertThat(ReferentPolicy.rejection("In the assignment under consideration"))
                .as("the assignment under consideration")
                .isEqualTo(ReferentPolicy.Rejection.PREPOSITIONAL_MODIFIER);
        assertThat(ReferentPolicy.rejection("In the other assignment under consideration"))
                .as("the other assignment under consideration")
                .isEqualTo(ReferentPolicy.Rejection.PREPOSITIONAL_MODIFIER);
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

    @Test
    @DisplayName("the control: a preposition followed by a NOUN still names a thing")
    void aPrepositionFollowedByANounStillNamesAThing() {
        // The published control (OD-071). The preposition opens the span, but the word
        // after it is a NOUN and not a determiner, so the span is not a modifier: it
        // names a thing and must stay a referent.
        assertThat(ReferentPolicy.rejection("In vitro photosynthesis"))
                .as("in vitro photosynthesis")
                .isEqualTo(ReferentPolicy.Rejection.NONE);
        // The rule's narrowness, measured: the same opener with a DETERMINER is a
        // modifier and is rejected. Without this, the control above would also hold
        // for a policy that rejects every span that opens with a preposition.
        assertThat(ReferentPolicy.rejection("In the plant"))
                .as("in the plant")
                .isEqualTo(ReferentPolicy.Rejection.PREPOSITIONAL_MODIFIER);
    }
}
