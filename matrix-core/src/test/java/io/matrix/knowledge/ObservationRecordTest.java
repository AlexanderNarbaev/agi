package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matrix.cognitive.microworld.ObservationDisposition;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W45.1 — the records of the occurrence and the decision, and the workbench's way in.
 *
 * <p>The store's new records are value objects with rules of their own: an occurrence needs
 * an identity, a case and an instant, and a decision needs an identity and says what it
 * decides. The workbench is the door the application comes through, and both of its new
 * doors are checked here: the one that records an observation with the episode and the
 * signal it belongs to, and the one that records a review's decision.</p>
 */
class ObservationRecordTest {

    /** The instant every record in this test carries. */
    private static final String INSTANT = "2026-11-04T00:00:00Z";

    /** The workspace the records live in. */
    private static final String WORKSPACE = "microworld";

    @TempDir
    Path directory;

    @Test
    @DisplayName("an occurrence is named after the evidence of the act")
    void anOccurrenceIsNamedAfterTheEvidenceOfTheAct() {
        assertThat(ObservationOccurrence.idForEvidence("evd-1")).isEqualTo("occ-evd-1");
        assertThatThrownBy(() -> ObservationOccurrence.idForEvidence("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an occurrence without an identity, a case or an instant is refused")
    void anOccurrenceWithoutAnIdentityACaseOrAnInstantIsRefused() {
        assertThatThrownBy(() -> new ObservationOccurrence(" ", WORKSPACE, "case-1", null, null,
                "src-1", "evd-1", INSTANT, ObservationDisposition.ELIGIBLE, "why"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObservationOccurrence("occ-1", WORKSPACE, " ", null, null,
                "src-1", "evd-1", INSTANT, ObservationDisposition.ELIGIBLE, "why"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObservationOccurrence("occ-1", WORKSPACE, "case-1", null, null,
                "src-1", "evd-1", " ", ObservationDisposition.ELIGIBLE, "why"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObservationOccurrence("occ-1", WORKSPACE, "case-1", null, null,
                "src-1", "evd-1", INSTANT, null, "why"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a decision that has not been taken has no previous disposition")
    void aDecisionThatHasNotBeenTakenHasNoPreviousDisposition() {
        ObservationAdjudication decision = ObservationAdjudication.decide("decision-1", WORKSPACE,
                "case-1", ObservationDisposition.ACCEPTED, "a review accepted it", "prd-1",
                "the owner", INSTANT, "rev-1", INSTANT, "req-1");
        assertThat(decision.previousDisposition()).isNull();
        assertThat(decision.disposition()).isEqualTo(ObservationDisposition.ACCEPTED);
        assertThat(decision.requestId()).isEqualTo("req-1");
    }

    @Test
    @DisplayName("a decision without an identity or a decision is refused")
    void aDecisionWithoutAnIdentityOrADecisionIsRefused() {
        assertThatThrownBy(() -> ObservationAdjudication.decide(" ", WORKSPACE, "case-1",
                ObservationDisposition.ACCEPTED, "why", null, "the owner", INSTANT, null, INSTANT,
                null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObservationAdjudication.decide("decision-1", WORKSPACE, "case-1",
                null, "why", null, "the owner", INSTANT, null, INSTANT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("two decisions carry the same content when they say the same thing")
    void twoDecisionsCarryTheSameContentWhenTheySayTheSameThing() {
        ObservationAdjudication one = ObservationAdjudication.decide("decision-1", WORKSPACE,
                "case-1", ObservationDisposition.ACCEPTED, "a review accepted it", "prd-1",
                "the owner", INSTANT, "rev-1", INSTANT, "req-1");
        ObservationAdjudication same = ObservationAdjudication.decide("decision-1", WORKSPACE,
                "case-1", ObservationDisposition.ACCEPTED, "a review accepted it", "prd-1",
                "the owner", INSTANT, "rev-1", INSTANT, "req-1");
        ObservationAdjudication other = ObservationAdjudication.decide("decision-1", WORKSPACE,
                "case-1", ObservationDisposition.REJECTED, "a review accepted it", "prd-1",
                "the owner", INSTANT, "rev-1", INSTANT, "req-1");

        assertThat(one.sameContentAs(same)).isTrue();
        assertThat(one.sameContentAs(other)).isFalse();
        assertThat(one.sameContentAs(null)).isFalse();
    }

    @Test
    @DisplayName("the previous disposition is the store's to compute and not part of the content")
    void thePreviousDispositionIsTheStoresToCompute() {
        ObservationAdjudication withoutPrevious = ObservationAdjudication.decide("decision-1",
                WORKSPACE, "case-1", ObservationDisposition.ACCEPTED, "why", null, "the owner",
                INSTANT, null, INSTANT, null);
        ObservationAdjudication withPrevious = new ObservationAdjudication("decision-1", WORKSPACE,
                "case-1", ObservationDisposition.UNRESOLVED, ObservationDisposition.ACCEPTED,
                "why", null, "the owner", INSTANT, null, INSTANT, null);

        assertThat(withoutPrevious.sameContentAs(withPrevious))
                .withFailMessage("the previous disposition is computed, so it is not content")
                .isTrue();
    }

    @Test
    @DisplayName("a conflict says what kind of conflict it is")
    void aConflictSaysWhatKindOfConflictItIs() {
        ObservationConflictException conflict = new ObservationConflictException(
                ObservationConflictException.Kind.DECISION, "the decision was reused");
        assertThat(conflict.kind()).isEqualTo(ObservationConflictException.Kind.DECISION);
        assertThat(conflict).hasMessageContaining("the decision was reused");
    }

    @Test
    @DisplayName("the workbench records an observation with the episode and the signal")
    void theWorkbenchRecordsAnObservationWithTheEpisodeAndTheSignal() {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(
                directory.resolve("workbench.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            String claimId = workbench.workspace("photosynthesis").claims().get(0).id();

            workbench.recordObservation("photosynthesis", claimId, "the lamp was on",
                    "power=1,switch=1", false, "case-lamp-on",
                    ObservationDisposition.UNRESOLVED, "the session held it", "ep-1", "sig-1");

            List<ObservationOccurrence> occurrences =
                    workbench.storeForValidation().occurrences("photosynthesis");
            assertThat(occurrences).hasSize(1);
            assertThat(occurrences.get(0).episodeId()).isEqualTo("ep-1");
            assertThat(occurrences.get(0).signalId()).isEqualTo("sig-1");
            assertThat(occurrences.get(0).caseId()).isEqualTo("case-lamp-on");
            assertThat(occurrences.get(0).disposition())
                    .isEqualTo(ObservationDisposition.UNRESOLVED);
        }
    }

    @Test
    @DisplayName("the workbench records a review's decision and refuses a reused one")
    void theWorkbenchRecordsAReviewsDecisionAndRefusesAReusedOne() {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(
                directory.resolve("workbench.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            String claimId = workbench.workspace("photosynthesis").claims().get(0).id();
            workbench.recordObservation("photosynthesis", claimId, "the lamp was on",
                    "power=1,switch=1", true, "case-lamp-on",
                    ObservationDisposition.UNRESOLVED, "the session held it", "ep-1", "sig-1");

            workbench.adjudicateObservation("photosynthesis", "case-lamp-on", "decision-1",
                    ObservationDisposition.ACCEPTED, "a review accepted it", "prd-ep-1",
                    "the owner", INSTANT, "rev-1", INSTANT, "req-1");

            KnowledgeStore store = workbench.storeForValidation();
            assertThat(store.caseDispositions("photosynthesis"))
                    .containsEntry("case-lamp-on", ObservationDisposition.ACCEPTED);
            assertThat(store.adjudicationsOfCase("photosynthesis", "case-lamp-on")).hasSize(2);

            // The same decision id with a different content is refused.
            assertThatThrownBy(() -> workbench.adjudicateObservation("photosynthesis",
                    "case-lamp-on", "decision-1", ObservationDisposition.REJECTED,
                    "a review rejected it", "prd-ep-1", "the owner", INSTANT, null, INSTANT,
                    "req-1"))
                    .isInstanceOf(ObservationConflictException.class);
        }
    }
}
