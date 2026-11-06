package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matrix.cognitive.microworld.ObservationDisposition;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W45.1 — the DEFECT: a re-observation silently reset the adjudication.
 *
 * <p>The micro-world derives a case id from the conditions AND the observed outcome, so the
 * SAME case id comes back in a later episode. The production path wrote the disposition with
 * {@code INSERT OR REPLACE}, so a later observation of the same fact overwrote the decision a
 * review had taken: the observation and its evidence survived, and the DECISION about whether
 * they may constrain the model did not.</p>
 *
 * <p>These tests are the proof of the fix. They drive the durable store, restart it, race it
 * and read it back, and they check three things: the adjudication survives, the history of
 * the decisions survives, and the two identities — the canonical case and the act of
 * observing it — stay apart.</p>
 */
class ObservationDispositionDurabilityTest {

    /** The instant every record in this test carries. */
    private static final String INSTANT = "2026-11-04T00:00:00Z";

    /** The instant the reviews of this test carry. */
    private static final String REVIEW_INSTANT = "2026-11-04T01:00:00Z";

    /** The workspace the records live in. */
    private static final String WORKSPACE = "microworld";

    /** The canonical case: the same conditions and the same outcome, observed twice. */
    private static final String CASE_ID = "case-lamp-on";

    @TempDir
    Path directory;

    // ── the defect, and the fix ──────────────────────────────────────────────

    @Test
    @DisplayName("a re-observation does not reset the adjudication a review took")
    void aReObservationDoesNotResetTheAdjudication() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            // 1. the observation is recorded as an unresolved conflict
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            assertThat(store.caseDispositions(WORKSPACE))
                    .containsEntry(CASE_ID, ObservationDisposition.UNRESOLVED);

            // 2. a review accepts it
            store.adjudicateObservation(ObservationAdjudication.decide("decision-1", WORKSPACE,
                    CASE_ID, ObservationDisposition.ACCEPTED,
                    "a review accepted the observation into the model", "prd-ep-1", "the owner",
                    REVIEW_INSTANT, "rev-1", REVIEW_INSTANT, "req-1"));
            assertThat(store.caseDispositions(WORKSPACE))
                    .containsEntry(CASE_ID, ObservationDisposition.ACCEPTED);

            // 3. a later episode observes the same conditions and the same outcome again
            recordObservation(store, "src-2", "evd-2", ObservationDisposition.ELIGIBLE);

            assertThat(store.caseDispositions(WORKSPACE))
                    .withFailMessage("the adjudication must survive the re-observation")
                    .containsEntry(CASE_ID, ObservationDisposition.ACCEPTED);
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("a re-observation does not reset a rejection either")
    void aReObservationDoesNotResetARejection() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            store.adjudicateObservation(ObservationAdjudication.decide("decision-1", WORKSPACE,
                    CASE_ID, ObservationDisposition.REJECTED,
                    "a review rejected the observation", null, "the owner", REVIEW_INSTANT, null,
                    REVIEW_INSTANT, "req-1"));

            recordObservation(store, "src-2", "evd-2", ObservationDisposition.ELIGIBLE);

            assertThat(store.caseDispositions(WORKSPACE))
                    .withFailMessage("the rejection must survive the re-observation")
                    .containsEntry(CASE_ID, ObservationDisposition.REJECTED);
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("a re-observation of an unresolved case leaves it unresolved")
    void aReObservationOfAnUnresolvedCaseLeavesItUnresolved() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            recordObservation(store, "src-2", "evd-2", ObservationDisposition.UNRESOLVED);

            assertThat(store.caseDispositions(WORKSPACE))
                    .containsEntry(CASE_ID, ObservationDisposition.UNRESOLVED);
            assertThat(store.occurrencesOfCase(WORKSPACE, CASE_ID))
                    .withFailMessage("both acts of observing are kept")
                    .hasSize(2);
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("the decision is not lost when the observation is recorded again")
    void theDecisionIsNotLostWhenTheObservationIsRecordedAgain() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            store.adjudicateObservation(ObservationAdjudication.decide("decision-1", WORKSPACE,
                    CASE_ID, ObservationDisposition.ACCEPTED, "a review accepted it", null,
                    "the owner", REVIEW_INSTANT, "rev-1", REVIEW_INSTANT, "req-1"));
            recordObservation(store, "src-2", "evd-2", ObservationDisposition.UNRESOLVED);

            // The history holds both decisions: the first one about the case, and the review.
            List<ObservationAdjudication> history = store.adjudicationsOfCase(WORKSPACE, CASE_ID);
            assertThat(history).hasSize(2);
            assertThat(history.get(0).disposition()).isEqualTo(ObservationDisposition.UNRESOLVED);
            assertThat(history.get(0).previousDisposition()).isNull();
            assertThat(history.get(1).disposition()).isEqualTo(ObservationDisposition.ACCEPTED);
            assertThat(history.get(1).previousDisposition())
                    .isEqualTo(ObservationDisposition.UNRESOLVED);
        } finally {
            store.close();
        }
    }

    // ── the decision id, the idempotency and the conflict ────────────────────

    @Test
    @DisplayName("the same decision delivered twice changes nothing")
    void theSameDecisionDeliveredTwiceChangesNothing() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            ObservationAdjudication decision = ObservationAdjudication.decide("decision-1", WORKSPACE,
                    CASE_ID, ObservationDisposition.ACCEPTED, "a review accepted it", null,
                    "the owner", REVIEW_INSTANT, "rev-1", REVIEW_INSTANT, "req-1");

            store.adjudicateObservation(decision);
            store.adjudicateObservation(decision);
            store.adjudicateObservation(decision);

            assertThat(store.adjudicationsOfCase(WORKSPACE, CASE_ID))
                    .withFailMessage("the decision is written once, however often it is delivered")
                    .hasSize(2);
            assertThat(store.caseDispositions(WORKSPACE))
                    .containsEntry(CASE_ID, ObservationDisposition.ACCEPTED);
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("the same decision id with a different content is a conflict")
    void theSameDecisionIdWithADifferentContentIsAConflict() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            store.adjudicateObservation(ObservationAdjudication.decide("decision-1", WORKSPACE,
                    CASE_ID, ObservationDisposition.ACCEPTED, "a review accepted it", null,
                    "the owner", REVIEW_INSTANT, "rev-1", REVIEW_INSTANT, "req-1"));

            assertThatThrownBy(() -> store.adjudicateObservation(ObservationAdjudication.decide(
                    "decision-1", WORKSPACE, CASE_ID, ObservationDisposition.REJECTED,
                    "a review rejected it", null, "the owner", REVIEW_INSTANT, null,
                    REVIEW_INSTANT, "req-1")))
                    .withFailMessage("a decision id may not be reused for another decision")
                    .isInstanceOf(ObservationConflictException.class);

            assertThat(store.caseDispositions(WORKSPACE))
                    .withFailMessage("the refused decision changed nothing")
                    .containsEntry(CASE_ID, ObservationDisposition.ACCEPTED);
            assertThat(store.adjudicationsOfCase(WORKSPACE, CASE_ID)).hasSize(2);
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("a rejected case may not become eligible again")
    void aRejectedCaseMayNotBecomeEligibleAgain() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            store.adjudicateObservation(ObservationAdjudication.decide("decision-1", WORKSPACE,
                    CASE_ID, ObservationDisposition.REJECTED, "a review rejected it", null,
                    "the owner", REVIEW_INSTANT, null, REVIEW_INSTANT, "req-1"));

            assertThatThrownBy(() -> store.adjudicateObservation(ObservationAdjudication.decide(
                    "decision-2", WORKSPACE, CASE_ID, ObservationDisposition.ELIGIBLE,
                    "make it model data", null, "the owner", REVIEW_INSTANT, null,
                    REVIEW_INSTANT, "req-2")))
                    .withFailMessage("only a review may overturn a rejection")
                    .isInstanceOf(ObservationConflictException.class);

            assertThat(store.caseDispositions(WORKSPACE))
                    .containsEntry(CASE_ID, ObservationDisposition.REJECTED);
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("a review that changes its mind keeps the earlier decision in the history")
    void aReviewThatChangesItsMindKeepsTheEarlierDecision() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            store.adjudicateObservation(ObservationAdjudication.decide("decision-1", WORKSPACE,
                    CASE_ID, ObservationDisposition.ACCEPTED, "a review accepted it", null,
                    "the owner", REVIEW_INSTANT, "rev-1", REVIEW_INSTANT, "req-1"));
            store.adjudicateObservation(ObservationAdjudication.decide("decision-2", WORKSPACE,
                    CASE_ID, ObservationDisposition.REJECTED, "a review changed its mind", null,
                    "the owner", REVIEW_INSTANT, null, REVIEW_INSTANT, "req-2"));

            List<ObservationAdjudication> history = store.adjudicationsOfCase(WORKSPACE, CASE_ID);
            assertThat(history).hasSize(3);
            assertThat(history.get(1).disposition()).isEqualTo(ObservationDisposition.ACCEPTED);
            assertThat(history.get(2).disposition()).isEqualTo(ObservationDisposition.REJECTED);
            assertThat(history.get(2).previousDisposition())
                    .isEqualTo(ObservationDisposition.ACCEPTED);
            assertThat(store.caseDispositions(WORKSPACE))
                    .containsEntry(CASE_ID, ObservationDisposition.REJECTED);
        } finally {
            store.close();
        }
    }

    // ── the two identities ───────────────────────────────────────────────────

    @Test
    @DisplayName("two acts of observing the same case are two occurrences and one case")
    void twoActsOfObservingTheSameCaseAreTwoOccurrencesAndOneCase() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.ELIGIBLE);
            recordObservation(store, "src-2", "evd-2", ObservationDisposition.ELIGIBLE);

            assertThat(store.cases(WORKSPACE))
                    .withFailMessage("the fact is one canonical case")
                    .hasSize(1);
            List<ObservationOccurrence> occurrences = store.occurrencesOfCase(WORKSPACE, CASE_ID);
            assertThat(occurrences).hasSize(2);
            assertThat(occurrences).extracting(ObservationOccurrence::id)
                    .containsExactly("occ-evd-1", "occ-evd-2");
            assertThat(occurrences).extracting(ObservationOccurrence::evidenceId)
                    .containsExactly("evd-1", "evd-2");
            assertThat(occurrences).extracting(ObservationOccurrence::sourceId)
                    .containsExactly("src-1", "src-2");
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("an occurrence keeps the episode and the signal it belongs to")
    void anOccurrenceKeepsTheEpisodeAndTheSignal() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            Source source = source("src-1");
            Evidence evidence = evidence("evd-1", "src-1");
            Case observation = observation("src-1");
            store.recordObservationOccurrence(WORKSPACE, source, evidence, observation,
                    ObservationDisposition.UNRESOLVED, "the session held it", "ep-1", "sig-1",
                    INSTANT);

            List<ObservationOccurrence> occurrences = store.occurrences(WORKSPACE);
            assertThat(occurrences).hasSize(1);
            assertThat(occurrences.get(0).episodeId()).isEqualTo("ep-1");
            assertThat(occurrences.get(0).signalId()).isEqualTo("sig-1");
            assertThat(occurrences.get(0).disposition())
                    .isEqualTo(ObservationDisposition.UNRESOLVED);
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("the cases that await a review are the unresolved ones and not the rejected ones")
    void theCasesThatAwaitAReviewAreTheUnresolvedOnes() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            recordObservation(store, "src-2", "evd-2", ObservationDisposition.UNRESOLVED);
            // The second case is rejected by a review.
            store.adjudicateObservation(ObservationAdjudication.decide("decision-1", WORKSPACE,
                    "case-rejected", ObservationDisposition.REJECTED, "a review rejected it", null,
                    "the owner", REVIEW_INSTANT, null, REVIEW_INSTANT, "req-1"));

            assertThat(store.casesAwaitingAdjudication(WORKSPACE))
                    .withFailMessage("a rejected case has already been adjudicated")
                    .containsExactly(CASE_ID);
        } finally {
            store.close();
        }
    }

    // ── the projection is a projection ───────────────────────────────────────

    @Test
    @DisplayName("the projection is rebuilt from the history and agrees with it")
    void theProjectionIsRebuiltFromTheHistory() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            store.adjudicateObservation(ObservationAdjudication.decide("decision-1", WORKSPACE,
                    CASE_ID, ObservationDisposition.ACCEPTED, "a review accepted it", null,
                    "the owner", REVIEW_INSTANT, "rev-1", REVIEW_INSTANT, "req-1"));

            assertThat(store.rebuildDispositionProjection(WORKSPACE))
                    .withFailMessage("the projection and the history must agree")
                    .isTrue();
            assertThat(store.caseDispositions(WORKSPACE))
                    .containsEntry(CASE_ID, ObservationDisposition.ACCEPTED);
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("a projection that disagrees with the history is reported and repaired")
    void aProjectionThatDisagreesWithTheHistoryIsReportedAndRepaired() throws Exception {
        Path database = directory.resolve("knowledge.sqlite");
        KnowledgeStore store = new KnowledgeStore(database);
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            store.adjudicateObservation(ObservationAdjudication.decide("decision-1", WORKSPACE,
                    CASE_ID, ObservationDisposition.ACCEPTED, "a review accepted it", null,
                    "the owner", REVIEW_INSTANT, "rev-1", REVIEW_INSTANT, "req-1"));

            // Something wrote over the projection behind the store's back.
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 Statement st = connection.createStatement()) {
                st.execute("UPDATE observation_disposition SET disposition='UNRESOLVED' "
                        + "WHERE case_id='" + CASE_ID + "'");
            }

            assertThat(store.rebuildDispositionProjection(WORKSPACE))
                    .withFailMessage("the disagreement must be reported, not hidden")
                    .isFalse();
            assertThat(store.caseDispositions(WORKSPACE))
                    .withFailMessage("the history is the authority")
                    .containsEntry(CASE_ID, ObservationDisposition.ACCEPTED);
            assertThat(store.rebuildDispositionProjection(WORKSPACE))
                    .withFailMessage("the rebuilt projection agrees with the history")
                    .isTrue();
        } finally {
            store.close();
        }
    }

    // ── the restart ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("a restart preserves the current state and the history")
    void aRestartPreservesTheCurrentStateAndTheHistory() {
        Path database = directory.resolve("knowledge.sqlite");
        KnowledgeStore store = new KnowledgeStore(database);
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            store.adjudicateObservation(ObservationAdjudication.decide("decision-1", WORKSPACE,
                    CASE_ID, ObservationDisposition.ACCEPTED, "a review accepted it", "prd-ep-1",
                    "the owner", REVIEW_INSTANT, "rev-1", REVIEW_INSTANT, "req-1"));
        } finally {
            store.close();
        }

        try (KnowledgeStore restarted = new KnowledgeStore(database)) {
            assertThat(restarted.caseDispositions(WORKSPACE))
                    .containsEntry(CASE_ID, ObservationDisposition.ACCEPTED);
            assertThat(restarted.adjudicationsOfCase(WORKSPACE, CASE_ID)).hasSize(2);
            assertThat(restarted.adjudications(WORKSPACE)).hasSize(2);
        }

        // And a re-observation after the restart still does not reset the decision.
        try (KnowledgeStore restarted = new KnowledgeStore(database)) {
            recordObservation(restarted, "src-2", "evd-2", ObservationDisposition.ELIGIBLE);
            assertThat(restarted.caseDispositions(WORKSPACE))
                    .containsEntry(CASE_ID, ObservationDisposition.ACCEPTED);
            assertThat(restarted.adjudicationsOfCase(WORKSPACE, CASE_ID)).hasSize(2);
        }
    }

    @Test
    @DisplayName("a replayed request does not reset the decision and does not double the occurrence")
    void aReplayedRequestDoesNotResetTheDecision() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            store.adjudicateObservation(ObservationAdjudication.decide("decision-1", WORKSPACE,
                    CASE_ID, ObservationDisposition.ACCEPTED, "a review accepted it", null,
                    "the owner", REVIEW_INSTANT, "rev-1", REVIEW_INSTANT, "req-1"));

            // The same request, replayed: the same source and the same evidence, so the same
            // act of observing.
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);

            assertThat(store.caseDispositions(WORKSPACE))
                    .containsEntry(CASE_ID, ObservationDisposition.ACCEPTED);
            assertThat(store.occurrencesOfCase(WORKSPACE, CASE_ID))
                    .withFailMessage("the replayed act is the same act")
                    .hasSize(1);
        } finally {
            store.close();
        }
    }

    // ── the concurrency ──────────────────────────────────────────────────────

    @Test
    @DisplayName("a recording and an adjudication of the same case do not undo each other")
    void aRecordingAndAnAdjudicationOfTheSameCaseDoNotUndoEachOther() throws Exception {
        Path database = directory.resolve("concurrent.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
        }

        KnowledgeStore recorder = new KnowledgeStore(database);
        KnowledgeStore reviewer = new KnowledgeStore(database);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> recorded = pool.submit(() -> {
                await(start);
                recordObservation(recorder, "src-2", "evd-2", ObservationDisposition.ELIGIBLE);
                return null;
            });
            Future<?> adjudicated = pool.submit(() -> {
                await(start);
                reviewer.adjudicateObservation(ObservationAdjudication.decide("decision-1",
                        WORKSPACE, CASE_ID, ObservationDisposition.ACCEPTED,
                        "a review accepted it", null, "the owner", REVIEW_INSTANT, "rev-1",
                        REVIEW_INSTANT, "req-1"));
                return null;
            });
            start.countDown();
            recorded.get(30, TimeUnit.SECONDS);
            adjudicated.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            recorder.close();
            reviewer.close();
        }

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.caseDispositions(WORKSPACE))
                    .withFailMessage("the adjudication and the re-observation must not undo "
                            + "each other")
                    .containsEntry(CASE_ID, ObservationDisposition.ACCEPTED);
            assertThat(store.occurrencesOfCase(WORKSPACE, CASE_ID)).hasSize(2);
            assertThat(store.adjudicationsOfCase(WORKSPACE, CASE_ID)).hasSize(2);
        }
    }

    @Test
    @DisplayName("two decisions that race over one case leave one of them standing")
    void twoDecisionsThatRaceOverOneCaseLeaveOneOfThemStanding() throws Exception {
        Path database = directory.resolve("racing.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
        }

        KnowledgeStore first = new KnowledgeStore(database);
        KnowledgeStore second = new KnowledgeStore(database);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> accepted = pool.submit(() -> {
                await(start);
                try {
                    first.adjudicateObservation(ObservationAdjudication.decide("decision-1",
                            WORKSPACE, CASE_ID, ObservationDisposition.ACCEPTED,
                            "a review accepted it", null, "the owner", REVIEW_INSTANT, "rev-1",
                            REVIEW_INSTANT, "req-1"));
                } catch (RuntimeException e) {
                    failure.compareAndSet(null, e);
                }
                return null;
            });
            Future<?> rejected = pool.submit(() -> {
                await(start);
                try {
                    second.adjudicateObservation(ObservationAdjudication.decide("decision-1",
                            WORKSPACE, CASE_ID, ObservationDisposition.REJECTED,
                            "a review rejected it", null, "the owner", REVIEW_INSTANT, null,
                            REVIEW_INSTANT, "req-1"));
                } catch (RuntimeException e) {
                    failure.compareAndSet(null, e);
                }
                return null;
            });
            start.countDown();
            accepted.get(30, TimeUnit.SECONDS);
            rejected.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            first.close();
            second.close();
        }

        assertThat(failure.get())
                .withFailMessage("the second decision must be refused, not applied")
                .isInstanceOf(ObservationConflictException.class);

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            Map<String, ObservationDisposition> dispositions = store.caseDispositions(WORKSPACE);
            assertThat(dispositions).hasSize(1);
            assertThat(dispositions.get(CASE_ID))
                    .isIn(ObservationDisposition.ACCEPTED, ObservationDisposition.REJECTED);
            assertThat(store.adjudicationsOfCase(WORKSPACE, CASE_ID))
                    .withFailMessage("only the decision that was taken is in the history")
                    .hasSize(2);
        }
    }

    // ── nothing is lost ──────────────────────────────────────────────────────

    @Test
    @DisplayName("the case, the source, the evidence and the prediction survive the review")
    void theCaseTheSourceTheEvidenceAndThePredictionSurviveTheReview() {
        KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"));
        try {
            recordObservation(store, "src-1", "evd-1", ObservationDisposition.UNRESOLVED);
            store.adjudicateObservation(ObservationAdjudication.decide("decision-1", WORKSPACE,
                    CASE_ID, ObservationDisposition.ACCEPTED, "a review accepted it", "prd-ep-1",
                    "the owner", REVIEW_INSTANT, "rev-1", REVIEW_INSTANT, "req-1"));

            assertThat(store.cases(WORKSPACE)).hasSize(1);
            assertThat(store.cases(WORKSPACE).get(0).id()).isEqualTo(CASE_ID);
            assertThat(store.sources(WORKSPACE)).hasSize(1);
            assertThat(store.sources(WORKSPACE).get(0).id()).isEqualTo("src-1");
            assertThat(store.evidence(WORKSPACE)).hasSize(1);
            assertThat(store.caseIdForEvidence("evd-1")).isEqualTo(CASE_ID);
            assertThat(store.adjudicationsOfCase(WORKSPACE, CASE_ID).get(1)
                    .contradictedPredictionId()).isEqualTo("prd-ep-1");
        } finally {
            store.close();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Waits for the latch, so that two threads really do start together. */
    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("the test was interrupted", e);
        }
    }

    /** Records one observation of the canonical case through the production path. */
    private void recordObservation(KnowledgeStore store, String sourceId, String evidenceId,
                                   ObservationDisposition disposition) {
        store.recordObservationAtomically(WORKSPACE, source(sourceId), evidence(evidenceId, sourceId),
                observation(sourceId), disposition, "the observation was recorded", INSTANT);
    }

    /** The source of an observation. */
    private Source source(String sourceId) {
        return new Source(sourceId, WORKSPACE, SourceKind.OBSERVATION, "an example",
                "power=1,switch=1", 40, 60, "the lamp was on", INSTANT, "hash", null, null, null);
    }

    /** The evidence an observation produces. */
    private Evidence evidence(String evidenceId, String sourceId) {
        return new Evidence(evidenceId, WORKSPACE, "clm-1", sourceId,
                EvidenceRelation.EXEMPLIFIES, "the lamp was on", 50, INSTANT);
    }

    /** The canonical case: the same conditions and the same outcome. */
    private Case observation(String sourceId) {
        return new Case(CASE_ID, WORKSPACE, "cpt-1", "the lamp was on", "power=1,switch=1",
                "the lamp was on", sourceId, Case.Origin.OBSERVED, INSTANT);
    }
}
