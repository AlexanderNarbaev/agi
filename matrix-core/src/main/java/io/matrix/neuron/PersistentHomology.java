package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * DESIGN-48 — Persistent Homology via Vietoris-Rips (Edelsbrunner 2010).
 * Topological features of point cloud at multiple scales.
 * Pure function (CONSTITUTION I).
 *
 * <p>Simplified: computes 0-dimensional persistent homology (connected
 * components merge as ε grows). Full persistent homology with 1D/2D
 * features would need boundary matrix reduction (Gauss elimination on
 * Z2); deferred to a separate RFC.
 */
public final class PersistentHomology {

    public record Point(double[] coords) {}

    public record PersistencePair(
            double birth,
            double death,
            int component1,
            int component2
    ) {}

    public record Diagram(List<PersistencePair> pairs0D, double maxEpsilon) {}

    private PersistentHomology() {}

    /**
     * Compute 0-dimensional persistent homology.
     * Uses Union-Find with edges sorted by Euclidean distance.
     */
    public static Diagram compute0D(List<Point> points, int maxEdges) {
        if (points == null) throw new IllegalArgumentException("null");
        int n = points.size();
        // Compute all pairwise distances
        List<double[]> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double d = distance(points.get(i), points.get(j));
                edges.add(new double[]{d, i, j});
            }
            if (edges.size() >= maxEdges) break;
        }
        // Sort by distance
        edges.sort((a, b) -> Double.compare(a[0], b[0]));
        // Union-Find
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        double[] birth = new double[n];
        for (int i = 0; i < n; i++) birth[i] = 0.0;
        List<PersistencePair> pairs = new ArrayList<>();
        double maxEps = 0;
        for (double[] e : edges) {
            double d = e[0];
            int a = (int) e[1], b = (int) e[2];
            int rootA = find(parent, a);
            int rootB = find(parent, b);
            if (rootA != rootB) {
                // Merge: younger component dies
                if (birth[rootA] < birth[rootB]) {
                    pairs.add(new PersistencePair(
                            Math.max(birth[rootA], birth[rootB]), d,
                            rootA, rootB));
                    parent[rootB] = rootA;
                } else {
                    pairs.add(new PersistencePair(
                            Math.max(birth[rootA], birth[rootB]), d,
                            rootA, rootB));
                    parent[rootA] = rootB;
                }
            }
            if (d > maxEps) maxEps = d;
        }
        return new Diagram(pairs, maxEps);
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static double distance(Point a, Point b) {
        double sum = 0;
        int n = Math.min(a.coords().length, b.coords().length);
        for (int i = 0; i < n; i++) {
            double d = a.coords()[i] - b.coords()[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }
}
