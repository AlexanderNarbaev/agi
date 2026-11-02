package io.matrix.cognitive.algorithms;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.2 — the selector: what it admits, what it refuses, and how it decides between the rest.
 */
class AlgorithmSelectorTest {

    private final AlgorithmRegistry registry = new AlgorithmRegistry()
            .register(new IntegerArithmeticMethod())
            .register(new DijkstraShortestPathMethod())
            .register(new BellmanFordShortestPathMethod());
    private final CostLedger ledger = new CostLedger();
    private final AlgorithmSelector selector = new AlgorithmSelector(registry, ledger);

    @Test
    void aMethodOutsideItsPreconditionIsNeverSelected() {
        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH,
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0),
                Set.of(), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.selectedAlgorithmId()).isNotEqualTo(DijkstraShortestPathMethod.ID);
        assertThat(decision.candidates()).anySatisfy(candidate -> {
            assertThat(candidate.algorithmId()).isEqualTo(DijkstraShortestPathMethod.ID);
            assertThat(candidate.selected()).isFalse();
            assertThat(candidate.verdict()).contains(Preconditions.NON_NEGATIVE_WEIGHTS);
        });
    }

    @Test
    void everyCandidateThatWasConsideredIsKeptInTheDecision() {
        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH,
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.candidates()).hasSize(2);
        assertThat(decision.candidates().stream().filter(AlgorithmDecision.Candidate::selected))
                .hasSize(1);
        assertThat(decision.rejected()).hasSize(1);
    }

    @Test
    void aFamilyWithNoAdmittedMethodIsRefusedAndTheBlockingPreconditionIsNamed() {
        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH,
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0),
                Set.of(), ComputeBudget.unbounded());

        AlgorithmRegistry onlyDijkstra = new AlgorithmRegistry().register(new DijkstraShortestPathMethod());
        AlgorithmDecision decision = new AlgorithmSelector(onlyDijkstra, ledger).decide(request);

        assertThat(decision.status()).isEqualTo(AlgorithmDecision.Status.REFUSED);
        assertThat(decision.selectedAlgorithmId()).isNull();
        assertThat(decision.reason()).contains(Preconditions.NON_NEGATIVE_WEIGHTS);
        assertThat(decision.candidates()).hasSize(1);
        assertThat(decision.candidates().get(0).selected()).isFalse();
    }

    @Test
    void aFamilyWithNoRegisteredMethodIsRefused() {
        ProblemRequest request = new ProblemRequest(
                "a.family.nobody.serves", "payload", Set.of(), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.status()).isEqualTo(AlgorithmDecision.Status.REFUSED);
        assertThat(decision.reason()).contains("no method is registered");
    }

    @Test
    void theCostBasisIsReportedForEveryCandidate() {
        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH,
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.candidates()).allSatisfy(candidate ->
                assertThat(candidate.verdict()).contains("basis DECLARED_COMPLEXITY"));
    }

    @Test
    void aMeasuredCostReplacesTheDeclaredComplexityAndTheDecisionSaysSo() {
        ledger.record(DijkstraShortestPathMethod.ID, 5_000L, true);

        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH,
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.candidates()).anySatisfy(candidate -> {
            assertThat(candidate.algorithmId()).isEqualTo(DijkstraShortestPathMethod.ID);
            assertThat(candidate.verdict()).contains("basis MEASURED");
        });
    }

    @Test
    void theCheaperMethodIsSelectedWhenTheQualityIsEqual() {
        // Both exact methods declare an accuracy of 1.0, so only the cost separates them.
        ledger.record(DijkstraShortestPathMethod.ID, 1_000L, true);
        ledger.record(BellmanFordShortestPathMethod.ID, 50_000L, true);

        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH,
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.selectedAlgorithmId()).isEqualTo(DijkstraShortestPathMethod.ID);
    }

    @Test
    void theIdDecidesWhenTheCostWeightMakesTheScoresEqual() {
        // A cost weight of zero says the cost does not matter, so the scores are equal and the
        // documented tie-break decides: the lexicographically smallest method id.
        ledger.record(DijkstraShortestPathMethod.ID, 1_000L, true);
        ledger.record(BellmanFordShortestPathMethod.ID, 50_000L, true);

        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH,
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request, 0.0);

        assertThat(decision.selectedAlgorithmId()).isEqualTo(BellmanFordShortestPathMethod.ID);
    }

    @Test
    void aMethodThatDoesNotFitTheBudgetIsNotChosenOverOneThatDoes() {
        ledger.record(DijkstraShortestPathMethod.ID, 500_000_000L, true);
        ledger.record(BellmanFordShortestPathMethod.ID, 1_000L, true);

        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH,
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS),
                ComputeBudget.millis(10L));

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.selectedAlgorithmId()).isEqualTo(BellmanFordShortestPathMethod.ID);
        assertThat(decision.status()).isEqualTo(AlgorithmDecision.Status.COMPLETED);
    }

    @Test
    void aFamilyWhereNothingFitsTheBudgetIsResourceLimited() {
        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH,
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS),
                ComputeBudget.millis(0L));

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.status()).isEqualTo(AlgorithmDecision.Status.RESOURCE_LIMITED);
        assertThat(decision.reason()).contains("did not fit the budget");
        assertThat(decision.selectedAlgorithmId()).isNotNull();
        assertThat(decision.fallbackAlgorithmId()).isNotNull();
    }

    @Test
    void theSameRequestAlwaysSelectsTheSameMethod() {
        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH,
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS), ComputeBudget.unbounded());

        String first = selector.decide(request).selectedAlgorithmId();
        String second = selector.decide(request).selectedAlgorithmId();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void aRefusedRequestSelectsNoMethod() {
        AlgorithmRegistry onlyDijkstra = new AlgorithmRegistry().register(new DijkstraShortestPathMethod());
        AlgorithmSelector refusing = new AlgorithmSelector(onlyDijkstra, ledger);

        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH,
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0),
                Set.of(), ComputeBudget.unbounded());

        AlgorithmSelector.Selection<ShortestPathProblem, ShortestPathProblem.Outcome> selection =
                refusing.select(request);

        assertThat(selection.method()).isNull();
        assertThat(selection.decision().status()).isEqualTo(AlgorithmDecision.Status.REFUSED);
    }

    @Test
    void selectReturnsTheChosenMethodWithItsDecision() {
        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.EXACT_ARITHMETIC,
                new IntegerArithmeticMethod.Input("2+2"),
                Set.of(Preconditions.WELL_FORMED_EXPRESSION), ComputeBudget.unbounded());

        AlgorithmSelector.Selection<IntegerArithmeticMethod.Input, IntegerArithmeticMethod.Output> selection =
                selector.select(request);

        assertThat(selection.method()).isNotNull();
        assertThat(selection.method().capability().id()).isEqualTo(IntegerArithmeticMethod.ID);
        assertThat(selection.decision().selectedAlgorithmId()).isEqualTo(IntegerArithmeticMethod.ID);
        assertThat(selection.method().execute(
                new IntegerArithmeticMethod.Input("2+2"), ComputeBudget.unbounded()).value())
                .isEqualTo(java.math.BigInteger.valueOf(4L));
    }

    @Test
    void aRefusedRequestIsRefusedEvenWhenTheMethodWouldHaveSaidOtherwise() {
        AlgorithmSelector.Selection<IntegerArithmeticMethod.Input, IntegerArithmeticMethod.Output> selection =
                selector.select(new ProblemRequest(
                        AlgorithmFamilies.EXACT_ARITHMETIC,
                        new IntegerArithmeticMethod.Input("2+2"),
                        Set.of(), ComputeBudget.unbounded()));

        assertThat(selection.method()).isNull();
        assertThat(selection.decision().status()).isEqualTo(AlgorithmDecision.Status.REFUSED);
        assertThat(selection.decision().reason()).contains(Preconditions.WELL_FORMED_EXPRESSION);
    }

    @Test
    void aBadCostWeightIsRefused() {
        assertThatThrownBy(() -> new AlgorithmSelector(registry, ledger, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AlgorithmSelector(registry, ledger, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> selector.decide(
                new ProblemRequest(AlgorithmFamilies.EXACT_ARITHMETIC, "x", Set.of(), ComputeBudget.unbounded()),
                -0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AlgorithmSelector(null, ledger))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AlgorithmSelector(registry, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDecisionCarriesTheBudgetItWasMadeUnder() {
        ComputeBudget budget = ComputeBudget.millis(42L);

        AlgorithmDecision decision = selector.decide(new ProblemRequest(
                AlgorithmFamilies.EXACT_ARITHMETIC,
                new IntegerArithmeticMethod.Input("1+1"),
                Set.of(Preconditions.WELL_FORMED_EXPRESSION), budget));

        assertThat(decision.budget()).isEqualTo(budget);
        assertThat(decision.problemFamily()).isEqualTo(AlgorithmFamilies.EXACT_ARITHMETIC);
        assertThat(decision.selectedAlgorithmVersion()).isEqualTo(IntegerArithmeticMethod.VERSION);
    }
}
