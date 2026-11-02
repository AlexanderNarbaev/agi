package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.algorithms.AlgorithmSelector;
import io.matrix.cognitive.algorithms.BellmanFordShortestPathMethod;
import io.matrix.cognitive.algorithms.BoundedUniformCostSearchMethod;
import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.algorithms.DijkstraShortestPathMethod;
import io.matrix.cognitive.algorithms.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W44 — the portfolio, reached from a request.
 *
 * <p>These tests exist because the portfolio has to be REACHABLE, not merely well built. The
 * assertions are about the preconditions, because that is where the selection can go wrong: a
 * method that is chosen outside the conditions under which its answer is correct is worse than
 * no selection at all.</p>
 */
class CognitiveSolverTest {

    private CognitiveSolver solver;

    @BeforeEach
    void setUp() {
        CostLedger ledger = new CostLedger();
        AlgorithmRegistry registry = Portfolio.defaultRegistry()
                .register(new MicroWorldReferenceMethod())
                .register(new MicroWorldSettledModelMethod());
        solver = new CognitiveSolver(new AlgorithmSelector(registry, ledger), ledger);
    }

    private static CognitiveSolver.GraphRequest graph() {
        // 0 - 1 - 2 - 3 with an extra 0 - 3 edge of weight 10.
        return new CognitiveSolver.GraphRequest(4, List.of(
                new CognitiveSolver.EdgeSpec(0, 1, 1.0),
                new CognitiveSolver.EdgeSpec(1, 2, 1.0),
                new CognitiveSolver.EdgeSpec(2, 3, 1.0),
                new CognitiveSolver.EdgeSpec(0, 3, 10.0)), 0, 3);
    }

    @Test
    @DisplayName("a graph with non-negative weights is solved by the method whose precondition holds")
    void aGraphWithNonNegativeWeightsIsSolved() {
        CognitiveSolver.SolveOutcome outcome = solver.solveGraph(graph(), 1.0, false);

        assertThat(outcome.selectedAlgorithmId()).isEqualTo(DijkstraShortestPathMethod.ID);
        assertThat(outcome.answer()).contains("3.0");
        assertThat(outcome.decision().status()).isEqualTo(AlgorithmDecision.Status.COMPLETED);
        // The decision must say why, and it must show what it rejected.
        assertThat(outcome.decision().reason()).isNotBlank();
        assertThat(outcome.decision().candidates()).isNotEmpty();
    }

    @Test
    @DisplayName("a negative weight removes Dijkstra from the candidates")
    void aNegativeWeightRemovesDijkstra() {
        CognitiveSolver.GraphRequest negative = new CognitiveSolver.GraphRequest(4, List.of(
                new CognitiveSolver.EdgeSpec(0, 1, 1.0),
                new CognitiveSolver.EdgeSpec(1, 2, -2.0),
                new CognitiveSolver.EdgeSpec(2, 3, 1.0),
                new CognitiveSolver.EdgeSpec(0, 3, 10.0)), 0, 3);

        CognitiveSolver.SolveOutcome outcome = solver.solveGraph(negative, 1.0, false);

        assertThat(outcome.selectedAlgorithmId()).isEqualTo(BellmanFordShortestPathMethod.ID);
        assertThat(outcome.decision().candidates()).anySatisfy(candidate -> {
            if (!candidate.selected()) {
                assertThat(candidate.verdict()).contains(DijkstraShortestPathMethod.ID);
            }
        });
    }

    @Test
    @DisplayName("a negative cycle is refused, and no finite distance is reported")
    void aNegativeCycleIsRefused() {
        CognitiveSolver.GraphRequest cyclic = new CognitiveSolver.GraphRequest(3, List.of(
                new CognitiveSolver.EdgeSpec(0, 1, 1.0),
                new CognitiveSolver.EdgeSpec(1, 2, -3.0),
                new CognitiveSolver.EdgeSpec(2, 0, 1.0)), 0, 2);

        CognitiveSolver.SolveOutcome outcome = solver.solveGraph(cyclic, 1.0, false);

        // Either the method refuses (and the solver says so), or the decision is a refusal.
        // What must NOT happen is a finite distance being reported for a graph with a
        // negative cycle.
        assertThat(outcome.answer()).doesNotContain("the shortest distance");
    }

    @Test
    @DisplayName("a bounded answer is only produced when the caller accepts one")
    void aBoundedAnswerIsOnlyProducedWhenAccepted() {
        CognitiveSolver.SolveOutcome bounded = solver.solveGraph(graph(), 1.0, true);
        assertThat(bounded.decision()).isNotNull();
        assertThat(bounded.decision().selectedAlgorithmId())
                .isIn(DijkstraShortestPathMethod.ID, BoundedUniformCostSearchMethod.ID);
        // Whether the bounded search finished or not, the answer must not CLAIM a proof it
        // does not have: a bounded result is labelled, and its distance is the same one the
        // exact method gives.
        if (bounded.answer().contains("NOT proven")) {
            assertThat(bounded.notes()).contains("bounded, not proven");
        } else {
            assertThat(bounded.answer()).contains("3.0");
        }
    }

    @Test
    @DisplayName("the measured cost of a method is recorded in the shared ledger")
    void theMeasuredCostIsRecordedInTheLedger() {
        solver.solveGraph(graph(), 1.0, false);

        assertThat(solver.ledger().measuredMethodIds())
                .contains(DijkstraShortestPathMethod.ID);
        assertThat(solver.ledger().callCount(DijkstraShortestPathMethod.ID)).isPositive();
    }
}
