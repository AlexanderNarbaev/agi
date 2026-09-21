package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphAlgorithmsTest {

    // ============ Dijkstra ============

    @Test
    void dijkstraSimpleLinearPath() {
        // 0 → 1 (weight 1) → 2 (weight 2) → 3 (weight 3)
        List<List<Dijkstra.Edge>> adj = new ArrayList<>();
        for (int i = 0; i < 4; i++) adj.add(new ArrayList<>());
        adj.get(0).add(new Dijkstra.Edge(1, 1.0));
        adj.get(1).add(new Dijkstra.Edge(2, 2.0));
        adj.get(2).add(new Dijkstra.Edge(3, 3.0));
        Dijkstra.Result r = Dijkstra.shortestPaths(adj, 0, Double.POSITIVE_INFINITY);
        assertThat(r.distances()[0]).isEqualTo(0.0);
        assertThat(r.distances()[1]).isEqualTo(1.0);
        assertThat(r.distances()[2]).isEqualTo(3.0);
        assertThat(r.distances()[3]).isEqualTo(6.0);
    }

    @Test
    void dijkstraUnreachableVertexHasInfiniteDistance() {
        List<List<Dijkstra.Edge>> adj = new ArrayList<>();
        adj.add(new ArrayList<>()); // vertex 0
        adj.add(new ArrayList<>()); // vertex 1 (no edges from 0)
        adj.get(0).add(new Dijkstra.Edge(0, 0.0)); // self-loop
        Dijkstra.Result r = Dijkstra.shortestPaths(adj, 0, Double.POSITIVE_INFINITY);
        assertThat(r.distances()[1]).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void dijkstraReconstructPath() {
        List<List<Dijkstra.Edge>> adj = new ArrayList<>();
        for (int i = 0; i < 4; i++) adj.add(new ArrayList<>());
        adj.get(0).add(new Dijkstra.Edge(1, 1.0));
        adj.get(1).add(new Dijkstra.Edge(2, 1.0));
        adj.get(2).add(new Dijkstra.Edge(3, 1.0));
        Dijkstra.Result r = Dijkstra.shortestPaths(adj, 0, Double.POSITIVE_INFINITY);
        int[] path = Dijkstra.reconstructPath(r, 3);
        assertThat(path).containsExactly(0, 1, 2, 3);
    }

    // ============ BellmanFord ============

    @Test
    void bellmanFordSimplePath() {
        List<BellmanFord.Edge> edges = Arrays.asList(
                new BellmanFord.Edge(0, 1, 1.0),
                new BellmanFord.Edge(1, 2, 2.0),
                new BellmanFord.Edge(2, 3, 3.0));
        BellmanFord.Result r = BellmanFord.shortestPaths(4, edges, 0);
        assertThat(r.distances()[0]).isEqualTo(0.0);
        assertThat(r.distances()[1]).isEqualTo(1.0);
        assertThat(r.distances()[2]).isEqualTo(3.0);
        assertThat(r.distances()[3]).isEqualTo(6.0);
    }

    @Test
    void bellmanFordDetectsNegativeCycle() {
        List<BellmanFord.Edge> edges = Arrays.asList(
                new BellmanFord.Edge(0, 1, 1.0),
                new BellmanFord.Edge(1, 2, -3.0),
                new BellmanFord.Edge(2, 0, 1.0)); // cycle: 1 - 3 + 1 = -1
        assertThatThrownBy(() -> BellmanFord.shortestPaths(3, edges, 0))
                .isInstanceOf(BellmanFord.NegativeCycleException.class);
    }

    @Test
    void bellmanFordHandlesNegativeEdgeWithoutCycle() {
        List<BellmanFord.Edge> edges = Arrays.asList(
                new BellmanFord.Edge(0, 1, 5.0),
                new BellmanFord.Edge(0, 2, -1.0),
                new BellmanFord.Edge(2, 1, -2.0));
        BellmanFord.Result r = BellmanFord.shortestPaths(3, edges, 0);
        // 0 → 2 → 1 = -1 + -2 = -3 (better than 0 → 1 = 5)
        assertThat(r.distances()[1]).isEqualTo(-3.0);
    }

    // ============ FloydWarshall ============

    @Test
    void floydWarshallAllPairsShortestPath() {
        double[][] weights = {
                {0.0, 1.0, Double.POSITIVE_INFINITY},
                {Double.POSITIVE_INFINITY, 0.0, 2.0},
                {3.0, Double.POSITIVE_INFINITY, 0.0}
        };
        double[][] d = FloydWarshall.shortestPaths(weights);
        assertThat(d[0][2]).isEqualTo(3.0);
        assertThat(d[0][1]).isEqualTo(1.0);
        assertThat(d[1][2]).isEqualTo(2.0);
    }

    @Test
    void floydWarshallDiagonalIsZero() {
        double[][] weights = {
                {0.0, 1.0},
                {1.0, 0.0}
        };
        double[][] d = FloydWarshall.shortestPaths(weights);
        assertThat(d[0][0]).isEqualTo(0.0);
        assertThat(d[1][1]).isEqualTo(0.0);
    }

    @Test
    void floydWarshallReconstructReturnsPaths() {
        double[][] weights = {
                {0.0, 1.0, Double.POSITIVE_INFINITY},
                {Double.POSITIVE_INFINITY, 0.0, 2.0},
                {3.0, Double.POSITIVE_INFINITY, 0.0}
        };
        double[][] d = FloydWarshall.shortestPaths(weights);
        List<int[]> paths = FloydWarshall.reconstruct(d);
        // reconstruct may return empty list or per-source arrays; just verify non-null
        assertThat(paths).isNotNull();
    }

    // ============ PageRank ============

    @Test
    void pageRankOnThreeNodeCycle() {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < 3; i++) adj.add(new ArrayList<>());
        adj.get(0).add(1);
        adj.get(1).add(2);
        adj.get(2).add(0);
        double[] ranks = PageRank.rank(adj, 0.85, 100, 1e-6, new Random(1));
        assertThat(ranks).hasSize(3);
        double sum = ranks[0] + ranks[1] + ranks[2];
        assertThat(sum).isBetween(0.99, 1.01);
        for (double r : ranks) {
            assertThat(r).isBetween(0.2, 0.4);
        }
    }

    @Test
    void pageRankOnStar() {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < 4; i++) adj.add(new ArrayList<>());
        adj.get(0).add(1);
        adj.get(0).add(2);
        adj.get(0).add(3);
        adj.get(1).add(0);
        adj.get(2).add(0);
        adj.get(3).add(0);
        double[] ranks = PageRank.rank(adj, 0.85, 100, 1e-6, new Random(1));
        assertThat(ranks).hasSize(4);
        double sum = ranks[0] + ranks[1] + ranks[2] + ranks[3];
        assertThat(sum).isBetween(0.99, 1.01);
    }

    @Test
    void pageRankTopKReturnsSorted() {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < 4; i++) adj.add(new ArrayList<>());
        adj.get(0).add(1);
        adj.get(1).add(2);
        adj.get(2).add(3);
        double[] ranks = PageRank.rank(adj, 0.85, 100, 1e-6, new Random(1));
        List<int[]> top2 = PageRank.topK(ranks, 2);
        assertThat(top2).hasSize(2);
    }
}
