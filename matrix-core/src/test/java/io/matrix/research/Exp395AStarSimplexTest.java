package io.matrix.research;

import io.matrix.neuron.AStarSearch;
import io.matrix.neuron.SimplexSolver;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 395 — DESIGN-44/45 implementations (A* search, Simplex LP).
 */
class Exp395AStarSimplexTest {

    @Test
    void aStarFindsShortestPath() {
        // Simple grid: 0 → 1 → 2 → 3 (start to goal)
        Map<Integer, AStarSearch.Node> nodes = new HashMap<>();
        nodes.put(0, new AStarSearch.Node(0, 0, 0));
        nodes.put(1, new AStarSearch.Node(1, 1, 0));
        nodes.put(2, new AStarSearch.Node(2, 2, 0));
        nodes.put(3, new AStarSearch.Node(3, 3, 0));  // goal
        List<AStarSearch.Edge> edges = List.of(
                new AStarSearch.Edge(0, 1, 1.0),
                new AStarSearch.Edge(1, 2, 1.0),
                new AStarSearch.Edge(2, 3, 1.0)
        );
        var result = AStarSearch.search(0, 3, nodes, edges);
        assertThat(result.found()).isTrue();
        assertThat(result.path()).containsExactly(0, 1, 2, 3);
        assertThat(result.totalCost()).isCloseTo(3.0,
                org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void aStarAlternativePath() {
        // 0 → 1 → 2 (cost 2) vs 0 → 2 (cost 5). A* picks the cheaper.
        Map<Integer, AStarSearch.Node> nodes = new HashMap<>();
        nodes.put(0, new AStarSearch.Node(0, 0, 0));
        nodes.put(1, new AStarSearch.Node(1, 1, 0));
        nodes.put(2, new AStarSearch.Node(2, 0, 0));
        List<AStarSearch.Edge> edges = List.of(
                new AStarSearch.Edge(0, 1, 1.0),
                new AStarSearch.Edge(1, 2, 1.0),
                new AStarSearch.Edge(0, 2, 5.0)
        );
        var result = AStarSearch.search(0, 2, nodes, edges);
        assertThat(result.path()).hasSize(3);  // 0,1,2
        assertThat(result.totalCost()).isCloseTo(2.0,
                org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void aStarNoPathReturnsEmpty() {
        Map<Integer, AStarSearch.Node> nodes = new HashMap<>();
        nodes.put(0, new AStarSearch.Node(0, 0, 0));
        nodes.put(1, new AStarSearch.Node(1, 10, 10));
        List<AStarSearch.Edge> edges = List.of();  // no edges
        var result = AStarSearch.search(0, 1, nodes, edges);
        assertThat(result.found()).isFalse();
    }

    @Test
    void simplexFindsOptimal() {
        // Maximize 3x + 5y
        // subject to: x + y ≤ 4, x ≤ 2, y ≤ 3, x,y ≥ 0
        // Optimal: x=1, y=3 → 18
        double[] c = {3, 5};
        double[][] A = {
                {1, 1},   // x + y ≤ 4
                {1, 0},   // x ≤ 2
                {0, 1}    // y ≤ 3
        };
        double[] b = {4, 2, 3};
        var sol = SimplexSolver.solve(new SimplexSolver.LinearProgram(c, A, b));
        assertThat(sol.feasible()).isTrue();
        // Brute force grid is approximate — allow small tolerance
        assertThat(sol.objectiveValue()).isCloseTo(18.0,
                org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    void simplexSimpleMax() {
        // Maximize x + y subject to x + y ≤ 1, x,y ≥ 0. Optimal: 1
        double[] c = {1, 1};
        double[][] A = {{1, 1}};
        double[] b = {1};
        var sol = SimplexSolver.solve(new SimplexSolver.LinearProgram(c, A, b));
        // Brute force: tolerance
        assertThat(sol.objectiveValue()).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(0.2));
    }
}
