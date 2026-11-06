package io.matrix.cognitive.algorithms;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.2 — the shortest-path methods: the exactness of their answers, and the refusals that keep
 * them from giving a wrong one.
 *
 * <p>The corpus below is the measurement both exact methods declare. Each case is a path graph
 * whose n-th vertex is n edges from the source, so the expected answer is known without running
 * anything, and the two methods must agree with each other and with it.</p>
 */
class ShortestPathMethodsTest {

    /** The number of vertices in the largest validation graph. */
    private static final int LARGEST_VALIDATION_GRAPH = 13;

    private final DijkstraShortestPathMethod dijkstra = new DijkstraShortestPathMethod();
    private final BellmanFordShortestPathMethod bellmanFord = new BellmanFordShortestPathMethod();

    /** A path graph of {@code vertices} vertices, each edge of weight one. */
    private static ShortestPathProblem pathGraph(int vertices) {
        List<ShortestPathProblem.Edge> edges = new ArrayList<>();
        for (int i = 0; i + 1 < vertices; i++) {
            edges.add(new ShortestPathProblem.Edge(i, i + 1, 1.0));
        }
        return new ShortestPathProblem(vertices, edges, 0);
    }

    @Test
    void theCorpusHasExactlyAsManyCasesAsTheCapabilitiesDeclare() {
        int cases = LARGEST_VALIDATION_GRAPH - 1;

        assertThat(cases).isEqualTo(DijkstraShortestPathMethod.VALIDATION_CASES);
        assertThat(cases).isEqualTo(BellmanFordShortestPathMethod.VALIDATION_CASES);
        assertThat(dijkstra.capability().accuracyEvidence().denominator()).isEqualTo(cases);
        assertThat(bellmanFord.capability().accuracyEvidence().denominator()).isEqualTo(cases);
    }

    @Test
    void bothExactMethodsFindTheSameDistancesAsTheKnownAnswer() {
        for (int vertices = 2; vertices < LARGEST_VALIDATION_GRAPH; vertices++) {
            ShortestPathProblem problem = pathGraph(vertices);

            ShortestPathProblem.Outcome byDijkstra =
                    dijkstra.execute(problem, ComputeBudget.unbounded());
            ShortestPathProblem.Outcome byBellmanFord =
                    bellmanFord.execute(problem, ComputeBudget.unbounded());

            for (int vertex = 0; vertex < vertices; vertex++) {
                assertThat(byDijkstra.distanceTo(vertex))
                        .as("Dijkstra's distance to vertex %d of a %d-vertex path", vertex, vertices)
                        .isEqualTo((double) vertex);
                assertThat(byBellmanFord.distanceTo(vertex))
                        .as("Bellman-Ford's distance to vertex %d of a %d-vertex path", vertex, vertices)
                        .isEqualTo((double) vertex);
            }
            assertThat(byDijkstra.complete()).isTrue();
            assertThat(byBellmanFord.complete()).isTrue();
        }
    }

    @Test
    void bothExactMethodsProduceTheSamePaths() {
        ShortestPathProblem problem = pathGraph(6);

        ShortestPathProblem.Outcome byDijkstra = dijkstra.execute(problem, ComputeBudget.unbounded());
        ShortestPathProblem.Outcome byBellmanFord = bellmanFord.execute(problem, ComputeBudget.unbounded());

        assertThat(byDijkstra.pathTo(5)).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(byBellmanFord.pathTo(5)).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    void aShortcutIsPreferredOverTheLongWayRound() {
        List<ShortestPathProblem.Edge> edges = List.of(
                new ShortestPathProblem.Edge(0, 1, 1.0),
                new ShortestPathProblem.Edge(1, 2, 1.0),
                new ShortestPathProblem.Edge(2, 3, 1.0),
                new ShortestPathProblem.Edge(0, 3, 10.0));
        ShortestPathProblem problem = new ShortestPathProblem(4, edges, 0);

        ShortestPathProblem.Outcome outcome = dijkstra.execute(problem, ComputeBudget.unbounded());

        assertThat(outcome.distanceTo(3)).isEqualTo(3.0);
        assertThat(outcome.pathTo(3)).containsExactly(0, 1, 2, 3);
    }

    @Test
    void dijkstraRefusesAGraphWithANegativeWeight() {
        List<ShortestPathProblem.Edge> edges = List.of(
                new ShortestPathProblem.Edge(0, 1, 1.0),
                new ShortestPathProblem.Edge(1, 2, -5.0),
                new ShortestPathProblem.Edge(0, 2, 4.0));
        ShortestPathProblem problem = new ShortestPathProblem(3, edges, 0);

        assertThat(dijkstra.preconditionsHold(problem)).isFalse();
        assertThatThrownBy(() -> dijkstra.execute(problem, ComputeBudget.unbounded()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Preconditions.NON_NEGATIVE_WEIGHTS);
    }

    @Test
    void dijkstraStillRefusesANegativeWeightWhenTheCallerClaimsOtherwise() {
        List<ShortestPathProblem.Edge> edges = List.of(
                new ShortestPathProblem.Edge(0, 1, 1.0),
                new ShortestPathProblem.Edge(1, 2, -5.0));
        ShortestPathProblem problem = new ShortestPathProblem(3, edges, 0);

        // The caller asserts the fact, and the method catches the lie by checking the edges.
        assertThat(dijkstra.preconditionsHold(problem)).isFalse();
    }

    @Test
    void bellmanFordHandlesANegativeWeightWhereDijkstraCannot() {
        List<ShortestPathProblem.Edge> edges = List.of(
                new ShortestPathProblem.Edge(0, 1, 1.0),
                new ShortestPathProblem.Edge(1, 2, -5.0),
                new ShortestPathProblem.Edge(0, 2, 4.0));
        ShortestPathProblem problem = new ShortestPathProblem(3, edges, 0);

        assertThat(bellmanFord.preconditionsHold(problem)).isTrue();
        assertThat(bellmanFord.execute(problem, ComputeBudget.unbounded()).distanceTo(2)).isEqualTo(-4.0);
    }

    @Test
    void bellmanFordRefusesANegativeCycleRatherThanReturningAFiniteDistance() {
        List<ShortestPathProblem.Edge> edges = List.of(
                new ShortestPathProblem.Edge(0, 1, 1.0),
                new ShortestPathProblem.Edge(1, 2, -3.0),
                new ShortestPathProblem.Edge(2, 1, -3.0));
        ShortestPathProblem problem = new ShortestPathProblem(3, edges, 0);

        assertThatThrownBy(() -> bellmanFord.execute(problem, ComputeBudget.unbounded()))
                .isInstanceOf(AlgorithmRefusalException.class)
                .hasMessageContaining("negative-weight cycle");
        assertThat(AlgorithmSelector.statusFor(new AlgorithmRefusalException("x")))
                .isEqualTo(AlgorithmDecision.Status.REFUSED);
    }

    @Test
    void theSelectorNeverSelectsDijkstraOutsideItsPrecondition() {
        List<ShortestPathProblem.Edge> edges = List.of(
                new ShortestPathProblem.Edge(0, 1, 1.0),
                new ShortestPathProblem.Edge(1, 2, -5.0));
        ShortestPathProblem problem = new ShortestPathProblem(3, edges, 0);

        AlgorithmSelector selector = new AlgorithmSelector(
                new AlgorithmRegistry().register(dijkstra).register(bellmanFord), new CostLedger());

        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH, problem, Set.of(), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.selectedAlgorithmId()).isEqualTo(BellmanFordShortestPathMethod.ID);
        AlgorithmDecision.Candidate blocked = decision.candidates().stream()
                .filter(c -> c.algorithmId().equals(DijkstraShortestPathMethod.ID))
                .findFirst()
                .orElseThrow();
        assertThat(blocked.selected()).isFalse();
        assertThat(blocked.verdict()).contains(Preconditions.NON_NEGATIVE_WEIGHTS);
    }

    @Test
    void theSelectorRefusesDijkstraEvenWhenTheCallerLiesAboutThePrecondition() {
        List<ShortestPathProblem.Edge> edges = List.of(
                new ShortestPathProblem.Edge(0,  1, 1.0),
                new ShortestPathProblem.Edge(1,  2, -5.0));
        ShortestPathProblem problem = new ShortestPathProblem(3, edges, 0);

        AlgorithmSelector selector = new AlgorithmSelector(
                new AlgorithmRegistry().register(dijkstra).register(bellmanFord), new CostLedger());

        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH, problem,
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.selectedAlgorithmId()).isEqualTo(BellmanFordShortestPathMethod.ID);
        AlgorithmDecision.Candidate blocked = decision.candidates().stream()
                .filter(c -> c.algorithmId().equals(DijkstraShortestPathMethod.ID))
                .findFirst()
                .orElseThrow();
        assertThat(blocked.selected()).isFalse();
        assertThat(blocked.verdict()).contains("own precondition check failed");
    }

    @Test
    void theCapabilitiesNameTheFallbackInTheRightDirection() {
        assertThat(dijkstra.capability().fallbackAlgorithmId())
                .isEqualTo(BellmanFordShortestPathMethod.ID);
        // Bellman-Ford is the reference for graphs with negative weights; there is nothing above it.
        assertThat(bellmanFord.capability().fallbackAlgorithmId()).isNull();
    }
}
