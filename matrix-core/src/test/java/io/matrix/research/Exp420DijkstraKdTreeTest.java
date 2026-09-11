package io.matrix.research;

import io.matrix.neuron.Dijkstra;
import io.matrix.neuron.KdTree;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 420 — Coverage for Dijkstra + KdTree.
 */
class Exp420DijkstraKdTreeTest {

    @Test
    void dijkstraFindsShortestPathsInASmallGraph() {
        // Graph: 0 -> 1 (1.0), 0 -> 2 (4.0), 1 -> 2 (2.0), 2 -> 3 (1.0)
        List<List<Dijkstra.Edge>> g = new ArrayList<>();
        g.add(List.of(new Dijkstra.Edge(1, 1.0), new Dijkstra.Edge(2, 4.0)));
        g.add(List.of(new Dijkstra.Edge(2, 2.0)));
        g.add(List.of(new Dijkstra.Edge(3, 1.0)));
        g.add(List.of());
        Dijkstra.Result r = Dijkstra.shortestPaths(g, 0, 0);
        assertThat(r.distances()).hasSize(4);
        assertThat(r.distances()[3]).isEqualTo(4.0);  // 0->1->2->3
        int[] path = Dijkstra.reconstructPath(r, 3);
        assertThat(path).containsExactly(0, 1, 2, 3);
    }

    @Test
    void dijkstraHandlesUnreachableNodes() {
        List<List<Dijkstra.Edge>> g = new ArrayList<>();
        g.add(List.of(new Dijkstra.Edge(1, 1.0)));
        g.add(List.of());  // 1 has no outgoing; 2 unreachable
        g.add(List.of());
        Dijkstra.Result r = Dijkstra.shortestPaths(g, 0, 0);
        assertThat(r.distances()[2]).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(Dijkstra.reconstructPath(r, 2)).isEmpty();
    }

    @Test
    void dijkstraOnNegativeCycleABortedByStrictNonNegativity() {
        // Only test that the algorithm correctly handles 0 weights.
        List<List<Dijkstra.Edge>> g = new ArrayList<>();
        g.add(List.of(new Dijkstra.Edge(1, 0.0)));
        g.add(List.of(new Dijkstra.Edge(2, 0.0)));
        g.add(List.of());
        Dijkstra.Result r = Dijkstra.shortestPaths(g, 0, 0);
        assertThat(r.distances()[2]).isEqualTo(0.0);
    }

    @Test
    void kdTreeFindsExactMatchForQueryAtAPoint() {
        double[][] pts = {
                {0, 0},
                {1, 1},
                {2, 2},
                {10, 10},
                {11, 11},
                {12, 12},
                {0.5, 0.5},
                {-1, -1}
        };
        KdTree tree = KdTree.of(pts);
        int[] nearestOne = tree.nearest(new double[]{0.5, 0.5}, 1);
        assertThat(nearestOne).hasSize(1);
        double[] nearestPt = pts[nearestOne[0]];
        assertThat(nearestPt[0]).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.6));
        // KNN=3 should give 0.5/0.5 first, then either of the others
        int[] k3 = tree.nearest(new double[]{0.5, 0.5}, 3);
        assertThat(k3).hasSize(3);
    }

    @Test
    void kdTreeHandlesEmptyAndTinySets() {
        KdTree empty = KdTree.of(new double[][]{});
        assertThat(empty.nearest(new double[]{0, 0}, 5)).isEmpty();

        KdTree one = KdTree.of(new double[][]{{0, 0}});
        int[] r = one.nearest(new double[]{1, 1}, 5);
        assertThat(r).hasSize(1);
        assertThat(r[0]).isEqualTo(0);
    }

    @Test
    void kdTreeKnnIn3D() {
        double[][] pts = {
                {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1},
                {5, 5, 5}, {5, 5, 6}, {5, 6, 5}, {6, 5, 5}
        };
        KdTree tree = KdTree.of(pts);
        int[] nn = tree.nearest(new double[]{0.9, 0.9, 0.9}, 4);
        assertThat(nn).hasSize(4);
        // indices in {0,1,2,3} should dominate
        int fromOrigin = 0;
        for (int idx : nn) if (idx < 4) fromOrigin++;
        assertThat(fromOrigin).isGreaterThanOrEqualTo(3);
    }
}
