package io.matrix.cognitive.algorithms;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.2 — the anytime method: it agrees with the exact method when it finishes, and it never
 * claims to have finished when it did not.
 */
class BoundedUniformCostSearchMethodTest {

    private final BoundedUniformCostSearchMethod bounded = new BoundedUniformCostSearchMethod();
    private final DijkstraShortestPathMethod dijkstra = new DijkstraShortestPathMethod();

    /** A path graph of {@code vertices} vertices, each edge of weight one. */
    private static ShortestPathProblem pathGraph(int vertices) {
        List<ShortestPathProblem.Edge> edges = new ArrayList<>();
        for (int i = 0; i + 1 < vertices; i++) {
            edges.add(new ShortestPathProblem.Edge(i, i + 1, 1.0));
        }
        return new ShortestPathProblem(vertices, edges, 0);
    }

    @Test
    void theCorpusHasExactlyAsManyCasesAsTheCapabilityDeclares() {
        assertThat(bounded.capability().accuracyEvidence().denominator())
                .isEqualTo(BoundedUniformCostSearchMethod.VALIDATION_CASES);
    }

    @Test
    void aSearchInsideItsBudgetAgreesWithTheExactMethod() {
        for (int vertices = 2; vertices < BoundedUniformCostSearchMethod.VALIDATION_CASES + 1; vertices++) {
            ShortestPathProblem problem = pathGraph(vertices);

            ShortestPathProblem.Outcome boundedOutcome =
                    bounded.execute(problem, ComputeBudget.unbounded());
            ShortestPathProblem.Outcome exactOutcome =
                    dijkstra.execute(problem, ComputeBudget.unbounded());

            assertThat(boundedOutcome.complete()).isTrue();
            for (int vertex = 0; vertex < vertices; vertex++) {
                assertThat(boundedOutcome.distanceTo(vertex))
                        .as("the bounded distance to vertex %d", vertex)
                        .isEqualTo(exactOutcome.distanceTo(vertex))
                        .isEqualTo((double) vertex);
            }
        }
    }

    @Test
    void aSearchThatRunsOutOfBudgetRefusesToPresentItsResultAsProven() {
        ShortestPathProblem problem = pathGraph(10);
        ComputeBudget tiny = new ComputeBudget(1_000L, 1_000L, 1_000L, 2);

        assertThatThrownBy(() -> bounded.execute(problem, tiny))
                .isInstanceOf(ResourceLimitException.class)
                .hasMessageContaining("node budget")
                .satisfies(thrown -> {
                    ResourceLimitException limit = (ResourceLimitException) thrown;
                    ShortestPathProblem.Outcome partial =
                            (ShortestPathProblem.Outcome) limit.partialResult();
                    assertThat(partial.complete()).isFalse();
                    assertThat(partial.note()).contains("not a proof");
                    // What it did find is still true: the source is at distance zero.
                    assertThat(partial.distanceTo(0)).isEqualTo(0.0);
                });
    }

    @Test
    void theResourceLimitMapsToTheResourceLimitedStatus() {
        assertThat(AlgorithmSelector.statusFor(new ResourceLimitException("x", null)))
                .isEqualTo(AlgorithmDecision.Status.RESOURCE_LIMITED);
    }

    @Test
    void theMethodRefusesAGraphWithANegativeWeight() {
        List<ShortestPathProblem.Edge> edges = List.of(
                new ShortestPathProblem.Edge(0, 1, 1.0),
                new ShortestPathProblem.Edge(1, 2, -5.0));
        ShortestPathProblem problem = new ShortestPathProblem(3, edges, 0);

        assertThat(bounded.preconditionsHold(problem)).isFalse();
        assertThatThrownBy(() -> bounded.execute(problem, ComputeBudget.unbounded()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theSelectorWillNotOfferTheBoundedMethodWithoutTheBoundedResultFact() {
        AlgorithmSelector selector = new AlgorithmSelector(
                new AlgorithmRegistry().register(dijkstra).register(bounded), new CostLedger());

        ProblemRequest withoutTheFact = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH, pathGraph(5),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(withoutTheFact);

        assertThat(decision.selectedAlgorithmId()).isEqualTo(DijkstraShortestPathMethod.ID);
        AlgorithmDecision.Candidate blocked = decision.candidates().stream()
                .filter(c -> c.algorithmId().equals(BoundedUniformCostSearchMethod.ID))
                .findFirst()
                .orElseThrow();
        assertThat(blocked.selected()).isFalse();
        assertThat(blocked.verdict()).contains(Preconditions.BOUNDED_RESULT_ACCEPTED);
    }

    @Test
    void theSelectorOffersTheBoundedMethodWhenTheCallerAcceptsABoundedResult() {
        AlgorithmSelector selector = new AlgorithmSelector(
                new AlgorithmRegistry().register(dijkstra).register(bounded), new CostLedger());

        ProblemRequest withTheFact = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH, pathGraph(5),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS, Preconditions.BOUNDED_RESULT_ACCEPTED),
                ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(withTheFact);

        assertThat(decision.selectedAlgorithmId()).isEqualTo(BoundedUniformCostSearchMethod.ID);
        assertThat(decision.candidates()).hasSize(2);
    }

    @Test
    void theBoundedMethodNamesTheExactMethodAsItsFallback() {
        assertThat(bounded.capability().fallbackAlgorithmId()).isEqualTo(DijkstraShortestPathMethod.ID);
        assertThat(bounded.capability().requires(Preconditions.BOUNDED_RESULT_ACCEPTED)).isTrue();
        assertThat(bounded.capability().requires(Preconditions.NON_NEGATIVE_WEIGHTS)).isTrue();
    }
}
