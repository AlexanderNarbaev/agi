package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.cognitive.microworld.ObservationDisposition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W45.1 — the state machine of an observation's disposition.
 *
 * <p>The transition table used to live only in the prose of the directive, and the store
 * enforced none of it. This test is the table, written out: every row of it is checked, and
 * the rows that are NOT in the table are checked too, because a state machine that only
 * proves what it allows proves nothing about what it refuses.</p>
 *
 * <p>The three predicates are checked separately, because they answer three different
 * questions. {@code constrainsModel} asks whether the observation may change what the model
 * believes; {@code awaitingAdjudication} asks whether a review is outstanding; and
 * {@code terminallyExcluded} asks whether the observation was rejected for good. The W45.0
 * wave had one method for the first question and used it for the other two as well, so a
 * rejected observation looked like a pending one.</p>
 */
class ObservationDispositionTransitionTest {

    @Test
    @DisplayName("a case may be born ELIGIBLE or UNRESOLVED and nothing else")
    void aCaseMayBeBornEligibleOrUnresolved() {
        assertThat(ObservationDisposition.canBeInitialDisposition(ObservationDisposition.ELIGIBLE))
                .isTrue();
        assertThat(ObservationDisposition.canBeInitialDisposition(ObservationDisposition.UNRESOLVED))
                .isTrue();
        assertThat(ObservationDisposition.canBeInitialDisposition(ObservationDisposition.ACCEPTED))
                .withFailMessage("a case cannot be born accepted: a review has not happened")
                .isFalse();
        assertThat(ObservationDisposition.canBeInitialDisposition(ObservationDisposition.REJECTED))
                .withFailMessage("a case cannot be born rejected: a review has not happened")
                .isFalse();
    }

    @Test
    @DisplayName("the transitions of the table are the transitions of the code")
    void theTransitionsOfTheTableAreTheTransitionsOfTheCode() {
        // (none) -> ELIGIBLE and (none) -> UNRESOLVED are the initial states.
        assertThat(ObservationDisposition.canBeInitialDisposition(ObservationDisposition.ELIGIBLE))
                .isTrue();
        assertThat(ObservationDisposition.canBeInitialDisposition(ObservationDisposition.UNRESOLVED))
                .isTrue();

        // ELIGIBLE -> UNRESOLVED: a later observation contradicts a settled belief.
        assertThat(ObservationDisposition.ELIGIBLE.canTransitionTo(ObservationDisposition.UNRESOLVED))
                .isTrue();
        // ELIGIBLE -> ACCEPTED / REJECTED: a review decides.
        assertThat(ObservationDisposition.ELIGIBLE.canTransitionTo(ObservationDisposition.ACCEPTED))
                .isTrue();
        assertThat(ObservationDisposition.ELIGIBLE.canTransitionTo(ObservationDisposition.REJECTED))
                .isTrue();

        // UNRESOLVED -> ACCEPTED / REJECTED: a review decides.
        assertThat(ObservationDisposition.UNRESOLVED.canTransitionTo(ObservationDisposition.ACCEPTED))
                .isTrue();
        assertThat(ObservationDisposition.UNRESOLVED.canTransitionTo(ObservationDisposition.REJECTED))
                .isTrue();

        // ACCEPTED -> REJECTED: a review changes its mind.
        assertThat(ObservationDisposition.ACCEPTED.canTransitionTo(ObservationDisposition.REJECTED))
                .isTrue();
        // ACCEPTED -> UNRESOLVED: a review re-opens it.
        assertThat(ObservationDisposition.ACCEPTED.canTransitionTo(ObservationDisposition.UNRESOLVED))
                .isTrue();

        // REJECTED -> UNRESOLVED: a review re-opens it, and that is recorded.
        assertThat(ObservationDisposition.REJECTED.canTransitionTo(ObservationDisposition.UNRESOLVED))
                .isTrue();
        // REJECTED -> ACCEPTED: a review changes its mind, recorded.
        assertThat(ObservationDisposition.REJECTED.canTransitionTo(ObservationDisposition.ACCEPTED))
                .isTrue();
    }

    @Test
    @DisplayName("a rejected observation never becomes eligible again")
    void aRejectedObservationNeverBecomesEligibleAgain() {
        assertThat(ObservationDisposition.REJECTED.canTransitionTo(ObservationDisposition.ELIGIBLE))
                .withFailMessage("a rejection may only be overturned by a review")
                .isFalse();
    }

    @Test
    @DisplayName("a held contradiction does not become model data by itself")
    void aHeldContradictionDoesNotBecomeModelDataByItself() {
        assertThat(ObservationDisposition.UNRESOLVED.canTransitionTo(ObservationDisposition.ELIGIBLE))
                .withFailMessage("an observation of unknown cause must not become model data")
                .isFalse();
    }

    @Test
    @DisplayName("a transition to nowhere is not a transition")
    void aTransitionToNowhereIsNotATransition() {
        for (ObservationDisposition disposition : ObservationDisposition.values()) {
            assertThat(disposition.canTransitionTo(null)).isFalse();
        }
    }

    @Test
    @DisplayName("a review that re-affirms the state it found leaves the state where it is")
    void aReviewThatReaffirmsTheStateLeavesItWhereItIs() {
        for (ObservationDisposition disposition : ObservationDisposition.values()) {
            assertThat(disposition.canTransitionTo(disposition))
                    .withFailMessage("re-affirming %s is not a move", disposition)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("the three predicates answer three different questions")
    void theThreePredicatesAnswerThreeDifferentQuestions() {
        // The model-eligibility rule.
        assertThat(ObservationDisposition.ELIGIBLE.constrainsModel()).isTrue();
        assertThat(ObservationDisposition.ACCEPTED.constrainsModel()).isTrue();
        assertThat(ObservationDisposition.UNRESOLVED.constrainsModel()).isFalse();
        assertThat(ObservationDisposition.REJECTED.constrainsModel()).isFalse();

        // The workflow state: pending.
        assertThat(ObservationDisposition.UNRESOLVED.awaitingAdjudication()).isTrue();
        assertThat(ObservationDisposition.ELIGIBLE.awaitingAdjudication()).isFalse();
        assertThat(ObservationDisposition.ACCEPTED.awaitingAdjudication()).isFalse();
        assertThat(ObservationDisposition.REJECTED.awaitingAdjudication())
                .withFailMessage("a rejected observation has already been adjudicated")
                .isFalse();

        // The workflow state: terminal.
        assertThat(ObservationDisposition.REJECTED.terminallyExcluded()).isTrue();
        assertThat(ObservationDisposition.ELIGIBLE.terminallyExcluded()).isFalse();
        assertThat(ObservationDisposition.UNRESOLVED.terminallyExcluded()).isFalse();
        assertThat(ObservationDisposition.ACCEPTED.terminallyExcluded()).isFalse();
    }

    @Test
    @DisplayName("isHeld is the model-eligibility rule and not a workflow state")
    void isHeldIsTheModelEligibilityRule() {
        // The W45.0 method: it says that the observation may not constrain the model, and it
        // says nothing about whether a review is outstanding.
        assertThat(ObservationDisposition.UNRESOLVED.isHeld()).isTrue();
        assertThat(ObservationDisposition.REJECTED.isHeld()).isTrue();
        assertThat(ObservationDisposition.ELIGIBLE.isHeld()).isFalse();
        assertThat(ObservationDisposition.ACCEPTED.isHeld()).isFalse();

        // The two workflow states are inside the held set and are told apart by the two
        // predicates that say which one is which.
        assertThat(ObservationDisposition.UNRESOLVED.isHeld())
                .isEqualTo(ObservationDisposition.UNRESOLVED.awaitingAdjudication()
                        || ObservationDisposition.UNRESOLVED.terminallyExcluded());
        assertThat(ObservationDisposition.REJECTED.isHeld())
                .isEqualTo(ObservationDisposition.REJECTED.awaitingAdjudication()
                        || ObservationDisposition.REJECTED.terminallyExcluded());
    }

    @Test
    @DisplayName("the conservative default is still the conservative one")
    void theConservativeDefaultIsStillTheConservativeOne() {
        assertThat(ObservationDisposition.conservativeDefault())
                .isEqualTo(ObservationDisposition.UNRESOLVED);
        assertThat(ObservationDisposition.conservativeDefault().constrainsModel()).isFalse();
    }
}
