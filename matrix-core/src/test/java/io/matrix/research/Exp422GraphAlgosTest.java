package io.matrix.research;

import io.matrix.neuron.BellmanFord;
import io.matrix.neuron.Dijkstra;
import io.matrix.neuron.FloydWarshall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RUN 422 — Coverage for BellmanFord + FloydWarshall + Dijkstra parity.
 */
class Exp422GraphAlgosTest {

    @Test
    void dijkstraAndBellmanFordAgreeOnGraphWithNoNegativeEdges() {
        // Same graph
        List<List<Dijkstra.Edge>> adj = new ArrayList<>();
        adj.add(List.of(new Dijkstra.Edge(1, 4.0), new Dijkstra.Edge(2, 1.0)));
        adj.add(List.of(new Dijkstra.Edge(3, 1.0)));
        adj.add(List.of(new Dijkstra.Edge(1, 2.0), new Dijkstra.Edge(3, 5.0)));
        adj.add(List.of());
        Dijkstra.Result dR = Dijkstra.shortestPaths(adj, 0, 0);

        List<BellmanFord.Edge> edges = new ArrayList<>();
        for (int u = 0; u < adj.size(); u++)
            for (Dijkstra.Edge e : adj.get(u))
                edges.add(new BellmanFord.Edge(u, e.to(), e.weight()));
        BellmanFord.Result bR = BellmanFord.shortestPaths(adj.size(), edges, 0);

        for (int i = 0; i < dR.distances().length; i++) {
            assertThat(dR.distances()[i]).isCloseTo(bR.distances()[i],
                    org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    @Test
    void bellmanFordHandlesNegativeEdges() {
        // Edge 0→1 (-1), 1→2 (-2), 2→3 (-3): total -6, vs 0→3 (0)
        List<BellmanFord.Edge> edges = List.of(
                new BellmanFord.Edge(0, 1, -1.0),
                new BellmanFord.Edge(1, 2, -2.0),
                new BellmanFord.Edge(2, 3, -3.0),
                new BellmanFord.Edge(0, 3, 0.0));
        BellmanFord.Result r = BellmanFord.shortestPaths(4, edges, 0);
        assertThat(r.distances()[3]).isEqualTo(-6.0);
    }

    @Test
    void bellmanFordDetectsNegativeCycle() {
        // 0→1 = 1, 1→0 = -3 ⇒ net cycle of -2
        List<BellmanFord.Edge> edges = List.of(
                new BellmanFord.Edge(0, 1, 1.0),
                new BellmanFord.Edge(1, 0, -3.0));
        assertThatThrownBy(() ->
                BellmanFord.shortestPaths(2, edges, 0))
                .isInstanceOf(BellmanFord.NegativeCycleException.class);
    }

    @Test
    void floydWarshallComputesAllPairsShortestPaths() {
        // 4-node graph as a weight matrix. -1 = no edge (Infinity).
        double INF = Double.POSITIVE_INFINITY;
        double[][] w = {
                {0,   1.0, INF, INF},
                {INF, 0,   1.0, INF},
                {INF, INF, 0,   1.0},
                {INF, INF, INF, 0}
        };
        double[][] d = FloydWarshall.shortestPaths(w);
        assertThat(d[0][1]).isEqualTo(1.0);
        assertThat(d[0][2]).isEqualTo(2.0);
        assertThat(d[0][3]).isEqualTo(3.0);
        // Self-loops after relaxation should be 0 (no negative cycle)
        for (int i = 0; i < 4; i++) assertThat(d[i][i]).isEqualTo(0.0);
    }

    @Test
    void floydWarshallDetectsNegativeCycle() {
        double INF = Double.POSITIVE_INFINITY;
        double[][] w = {
                {0,    1.0,  INF},
                {INF,  0,  -3.0},
                {1.0,  INF,   0}
        };
        // Path 2→0→1→2 has weight 1+1-3 = -1, ⇒ cycle
        assertThatThrownBy(() -> FloydWarshall.shortestPaths(w))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void floydWarshallHandlesDisconnectedNodes() {
        double INF = Double.POSITIVE_INFINITY;
        double[][] w = {
                {0, 1.0, INF, INF},
                {INF, 0, INF, INF},
                {INF, INF, 0, 1.0},
                {INF, INF, INF, 0}
        };
        double[][] d = FloydWarshall.shortestPaths(w);
        assertThat(d[0][1]).isEqualTo(1.0);
        assertThat(d[0][2]).isEqualTo(Double.POSITIVE_INFINITY);
    }
}
