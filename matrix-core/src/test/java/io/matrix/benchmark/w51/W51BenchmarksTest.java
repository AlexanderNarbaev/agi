package io.matrix.benchmark.w51;

import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.algorithms.AlgorithmSelector;
import io.matrix.learning.env.EpisodeReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 §10 — the three measured scenarios, checked before they are believed.
 *
 * <p>The numbers in {@code W51-PERFORMANCE.md} are only worth reading if the scenarios
 * measured what they claim to measure. These tests check that: the selector really selects,
 * the episode really learns and really is reproducible, and the bridge really accepts the
 * message it is given.</p>
 */
class W51BenchmarksTest {

    /** The number of warm-up rounds the tests use. */
    private static final int WARMUP = 5;

    /** The number of measured rounds the tests use. */
    private static final int ROUNDS = 50;

    @Test
    @DisplayName("the measured selector really chooses a method")
    void theSelectorReallyChooses() {
        AlgorithmSelector selector = W51Benchmarks.selector(W51Benchmarks.SMALLEST_PORTFOLIO);

        AlgorithmDecision decision = selector.decide(W51Benchmarks.request());

        assertNotNull(decision.selectedAlgorithmId());
        assertEquals(AlgorithmDecision.Status.COMPLETED, decision.status());
        assertFalse(decision.candidates().isEmpty());
    }

    @Test
    @DisplayName("a bigger portfolio means more candidates in the decision")
    void aBiggerPortfolioMeansMoreCandidates() {
        AlgorithmSelector small = W51Benchmarks.selector(W51Benchmarks.SMALLEST_PORTFOLIO);
        AlgorithmSelector large = W51Benchmarks.selector(W51Benchmarks.LARGEST_PORTFOLIO);

        int smallCandidates = small.decide(W51Benchmarks.request()).candidates().size();
        int largeCandidates = large.decide(W51Benchmarks.request()).candidates().size();

        assertTrue(largeCandidates > smallCandidates,
                "the larger portfolio must be considered in full");
    }

    @Test
    @DisplayName("the ledger reports the cost it was given")
    void theLedgerReportsWhatItWasGiven() {
        assertEquals(W51Benchmarks.MEASURED_COST_NANOS,
                W51Benchmarks.ledger().measuredNanos(W51Benchmarks.methodId(0)));
    }

    @Test
    @DisplayName("the measured episode learns and settles")
    void theEpisodeLearnsAndSettles() {
        EpisodeReport report = W51Benchmarks.episodeRunner().run();

        assertTrue(report.steps() > 0, "an episode that took no step learned nothing");
        assertEquals(EpisodeReport.TerminationReason.MODEL_SETTLED, report.terminationReason());
        assertEquals(1, report.finalSurvivors());
        assertTrue(report.utility() > 0.0);
    }

    @Test
    @DisplayName("two episodes with the same seed are the same episode")
    void twoEpisodesWithTheSameSeedAreTheSameEpisode() {
        EpisodeReport first = W51Benchmarks.episodeRunner().run();
        EpisodeReport second = W51Benchmarks.episodeRunner().run();

        assertEquals(first, second, "the measured path must be deterministic");
        assertEquals(first.steps(), second.steps());
        assertEquals(first.terminationReason(), second.terminationReason());
    }

    @Test
    @DisplayName("the bridge accepts the message it is given")
    void theBridgeAcceptsTheMessage() {
        var receiver = W51Benchmarks.bridgeReceiver();
        var messages = W51Benchmarks.bridgeMessages(2);

        var first = receiver.receive(messages.get(0), W51Benchmarks.BRIDGE_NOW_MILLIS);
        var second = receiver.receive(messages.get(1), W51Benchmarks.BRIDGE_NOW_MILLIS);

        assertTrue(first.accepted());
        assertTrue(second.accepted());
        assertEquals(2, receiver.auditLog().size());
    }

    @Test
    @DisplayName("every scenario produces a measurement")
    void everyScenarioProducesAMeasurement() {
        BenchResult selection = W51Benchmarks.methodSelection(
                W51Benchmarks.MIDDLE_PORTFOLIO, WARMUP, ROUNDS);
        BenchResult ledger = W51Benchmarks.costLedgerLookup(WARMUP, ROUNDS);
        W51Benchmarks.EpisodeMeasurement episode =
                W51Benchmarks.episode(WARMUP, ROUNDS);
        BenchResult bridge = W51Benchmarks.bridge(WARMUP, ROUNDS);

        assertEquals(ROUNDS, selection.samples());
        assertEquals(ROUNDS, ledger.samples());
        assertEquals(ROUNDS, episode.bench().samples());
        assertEquals(ROUNDS, bridge.samples());
        assertTrue(selection.averageNanos() >= 0.0);
        assertTrue(bridge.averageNanos() >= 0.0);
    }

    @Test
    @DisplayName("the episode measurement reports the steps and the allocation per step")
    void theEpisodeMeasurementReportsTheSteps() {
        W51Benchmarks.EpisodeMeasurement measurement = W51Benchmarks.episode(WARMUP, ROUNDS);

        assertTrue(measurement.steps() > 0);
        assertTrue(measurement.allocatedBytesPerStep() >= 0.0);
    }
}
