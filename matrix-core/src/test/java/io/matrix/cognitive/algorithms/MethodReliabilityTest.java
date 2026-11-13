package io.matrix.cognitive.algorithms;

import io.matrix.cognitive.CognitiveStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * W53 — the reliability of a method: the interval, the states, and the rule that keeps an
 * unusable estimate out of the score.
 *
 * <p><b>What is pinned here.</b> The estimator is the one the wave's research chose and
 * published — the Wilson score interval at the 90 % level, its lower limit, and the width as
 * the statement about the evidence ({@code
 * docs-v2/research/W53-RESEARCH-D-RELIABILITY-CALIBRATION.md} §4). The numbers in this test
 * are the ones that document publishes: one decided trial gives the interval
 * [0.270, 1.000] of width 0.730, and twenty-seven of thirty gives [0.774, 0.959] of width
 * 0.185 — which is the difference between an estimate that cannot be acted on and one that
 * can.</p>
 *
 * <p><b>What the negative control is.</b> The verdicts are recorded <b>pooled</b> in one test
 * and <b>in the stratum</b> in another, and the score must follow: a pooled rate is not
 * evidence for a request. Without the stratum the measured bound would replace the declared
 * accuracy and the score would change; with it, the score must not.</p>
 */
class MethodReliabilityTest {

    /** The workspace the verdicts belong to. */
    private static final String WORKSPACE = "microworld";

    /** The family the selector asks for. */
    private static final String FAMILY = AlgorithmFamilies.SHORTEST_PATH;

    /** The method the verdicts are recorded for. */
    private static final String METHOD = "shortest-path-dijkstra";

    /** The version of the capability the verdicts are recorded for. */
    private static final String VERSION = "1.0.0";

    @Test
    @DisplayName("one decided trial gives the interval the research published, and it cannot be acted on")
    void oneDecidedTrialCannotBeActedOn() {
        MethodReliability reliability = MethodReliability.of(
                new MethodVerdict(METHOD, FAMILY, VERSION, 1, 1, 0, 0, 0, 0, 0, 0));

        assertThat(reliability.lowerBound()).isCloseTo(0.270, within(0.001));
        assertThat(reliability.upperBound()).isCloseTo(1.000, within(0.001));
        assertThat(reliability.width()).isCloseTo(0.730, within(0.001));
        assertThat(reliability.state()).isEqualTo(MethodReliability.State.NOT_YET_USABLE);
        assertThat(reliability.decisions()).isEqualTo(1);
        assertThat(reliability.jeffreysMean()).isCloseTo(0.750, within(0.001));
    }

    @Test
    @DisplayName("twenty-seven of thirty decided trials give a narrow interval that may be acted on")
    void twentySevenOfThirtyIsUsable() {
        MethodReliability reliability = MethodReliability.of(
                new MethodVerdict(METHOD, FAMILY, VERSION, 30, 27, 3, 0, 0, 0, 0, 0));

        assertThat(reliability.lowerBound()).isCloseTo(0.774, within(0.001));
        assertThat(reliability.upperBound()).isCloseTo(0.959, within(0.001));
        assertThat(reliability.width()).isCloseTo(0.185, within(0.001));
        assertThat(reliability.state()).isEqualTo(MethodReliability.State.ESTIMATED);
        assertThat(reliability.jeffreysMean()).isCloseTo(0.887, within(0.001));
        assertThat(reliability.reason()).contains("90 % lower bound");
    }

    @Test
    @DisplayName("a method that was never observed has no evidence at all")
    void aMethodThatWasNeverObservedHasNoEvidence() {
        MethodReliability reliability = MethodReliability.neverMeasured(METHOD);

        assertThat(reliability.state()).isEqualTo(MethodReliability.State.NO_EVIDENCE);
        assertThat(reliability.reason()).contains("no measured reliability");
    }

    @Test
    @DisplayName("the abstentions and the technical outcomes stay out of the denominator")
    void theAbstentionsStayOutOfTheDenominator() {
        CostLedger ledger = new CostLedger();
        ledger.record(METHOD, 10L, MethodOutcome.VALIDATED, FAMILY, VERSION);
        ledger.record(METHOD, 10L, MethodOutcome.VALIDATED, FAMILY, VERSION);
        ledger.record(METHOD, 10L, MethodOutcome.INCORRECT, FAMILY, VERSION);
        ledger.record(METHOD, 10L, MethodOutcome.ABSTAINED, FAMILY, VERSION);
        ledger.record(METHOD, 10L, MethodOutcome.TECHNICAL_FAILURE, FAMILY, VERSION);
        ledger.record(METHOD, 10L, MethodOutcome.UNVERIFIED, FAMILY, VERSION);

        MethodReliability reliability = ledger.reliability(METHOD, FAMILY, VERSION);

        assertThat(reliability.calls()).isEqualTo(6);
        assertThat(reliability.decisions()).isEqualTo(3);
        assertThat(reliability.validated()).isEqualTo(2);
        assertThat(reliability.abstentions()).isEqualTo(1);
        assertThat(reliability.notEvidence()).isEqualTo(2);
        assertThat(reliability.state()).isEqualTo(MethodReliability.State.NOT_YET_USABLE);
    }

    @Test
    @DisplayName("the verdicts of a method are counted by kind")
    void theVerdictsAreCountedByKind(@TempDir Path directory) {
        try (CognitiveStore store = new CognitiveStore(directory.resolve("cognitive.sqlite"))) {
            CostLedger ledger = new CostLedger(store, WORKSPACE);
            ledger.record(METHOD, 10L, MethodOutcome.VALIDATED, FAMILY, VERSION);
            ledger.record(METHOD, 10L, MethodOutcome.PARTIAL, FAMILY, VERSION);
            ledger.record(METHOD, 10L, MethodOutcome.UNSUPPORTED, FAMILY, VERSION);
            ledger.record(METHOD, 10L, MethodOutcome.REFUSED, FAMILY, VERSION);

            List<MethodVerdict> verdicts = store.verdicts(WORKSPACE);

            assertThat(verdicts).hasSize(1);
            MethodVerdict verdict = verdicts.get(0);
            assertThat(verdict.methodId()).isEqualTo(METHOD);
            assertThat(verdict.family()).isEqualTo(FAMILY);
            assertThat(verdict.capabilityVersion()).isEqualTo(VERSION);
            assertThat(verdict.calls()).isEqualTo(4);
            assertThat(verdict.validated()).isEqualTo(1);
            assertThat(verdict.partial()).isEqualTo(1);
            assertThat(verdict.unsupported()).isEqualTo(1);
            assertThat(verdict.abstained()).isZero();
            assertThat(verdict.notEvidence()).isEqualTo(1);
            assertThat(verdict.pooled()).isFalse();
        }
    }

    @Test
    @DisplayName("a verdict recorded without a stratum is not evidence for a request")
    void aPooledVerdictIsNotEvidenceForAStratum() {
        CostLedger ledger = new CostLedger();
        for (int i = 0; i < 30; i++) {
            ledger.record(METHOD, 10L, MethodOutcome.VALIDATED);
        }

        MethodReliability pooled = ledger.reliability(METHOD, FAMILY, VERSION);

        assertThat(pooled.state()).isEqualTo(MethodReliability.State.NO_EVIDENCE);
        assertThat(pooled.reason()).contains("pooled");
    }

    @Test
    @DisplayName("the verdicts outlive the process")
    void theVerdictsOutliveTheProcess(@TempDir Path directory) {
        Path database = directory.resolve("cognitive.sqlite");

        try (CognitiveStore store = new CognitiveStore(database)) {
            CostLedger ledger = new CostLedger(store, WORKSPACE);
            for (int i = 0; i < 27; i++) {
                ledger.record(METHOD, 1_000L, MethodOutcome.VALIDATED, FAMILY, VERSION);
            }
            for (int i = 0; i < 3; i++) {
                ledger.record(METHOD, 1_000L, MethodOutcome.INCORRECT, FAMILY, VERSION);
            }
            assertThat(ledger.durable()).isTrue();
        }

        try (CognitiveStore store = new CognitiveStore(database)) {
            CostLedger ledger = new CostLedger(store, WORKSPACE);

            MethodReliability reliability = ledger.reliability(METHOD, FAMILY, VERSION);
            assertThat(reliability.calls()).isEqualTo(30);
            assertThat(reliability.decisions()).isEqualTo(30);
            assertThat(reliability.validated()).isEqualTo(27);
            assertThat(reliability.state()).isEqualTo(MethodReliability.State.ESTIMATED);
            assertThat(reliability.lowerBound()).isCloseTo(0.774, within(0.001));
        }
    }

    @Test
    @DisplayName("the score keeps the declared accuracy while there is no evidence")
    void theScoreKeepsTheDeclaredAccuracyWhileThereIsNoEvidence() {
        AlgorithmRegistry registry = new AlgorithmRegistry()
                .register(new DijkstraShortestPathMethod());
        AlgorithmSelector selector = new AlgorithmSelector(registry, new CostLedger());

        AlgorithmDecision decision = selector.decide(shortestPathRequest());

        assertThat(decision.selectedAlgorithmId()).isEqualTo(DijkstraShortestPathMethod.ID);
        assertThat(decision.reason()).contains("DECLARED");
        assertThat(decision.reason()).contains("NO_EVIDENCE");
    }

    @Test
    @DisplayName("the measured bound replaces the declared accuracy once the evidence bears it")
    void theMeasuredBoundReplacesTheDeclaredAccuracy() {
        AlgorithmRegistry registry = new AlgorithmRegistry()
                .register(new DijkstraShortestPathMethod());
        CostLedger ledger = new CostLedger();
        for (int i = 0; i < 27; i++) {
            ledger.record(DijkstraShortestPathMethod.ID, 1_000L, MethodOutcome.VALIDATED,
                    FAMILY, DijkstraShortestPathMethod.VERSION);
        }
        for (int i = 0; i < 3; i++) {
            ledger.record(DijkstraShortestPathMethod.ID, 1_000L, MethodOutcome.INCORRECT,
                    FAMILY, DijkstraShortestPathMethod.VERSION);
        }
        AlgorithmSelector selector = new AlgorithmSelector(registry, ledger);

        AlgorithmDecision decision = selector.decide(shortestPathRequest());

        assertThat(decision.reason()).contains("MEASURED");
        assertThat(decision.reason()).contains("90 % lower bound");
        assertThat(decision.candidates()).anySatisfy(candidate ->
                assertThat(candidate.verdict()).contains("MEASURED"));
    }

    @Test
    @DisplayName("a pooled rate does not move the score — the refusal to pool")
    void aPooledRateDoesNotMoveTheScore() {
        AlgorithmRegistry registry = new AlgorithmRegistry()
                .register(new DijkstraShortestPathMethod());
        CostLedger ledger = new CostLedger();
        for (int i = 0; i < 30; i++) {
            ledger.record(DijkstraShortestPathMethod.ID, 1_000L, MethodOutcome.VALIDATED);
        }
        AlgorithmSelector selector = new AlgorithmSelector(registry, ledger);

        AlgorithmDecision decision = selector.decide(shortestPathRequest());

        assertThat(decision.reason()).contains("on the DECLARED basis");
        assertThat(decision.reason()).contains("kept out of the score");
        assertThat(decision.reason()).contains("pooled");
    }

    private static ProblemRequest shortestPathRequest() {
        return new ProblemRequest(
                FAMILY,
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS), ComputeBudget.unbounded());
    }
}
