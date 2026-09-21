package io.matrix.ktopo;

import java.util.Objects;

/**
 * Small undirected weighted graph used as input for SPEC-003 topology analysis.
 *
 * <p>Nodes are indexed {@code 0..n-1}. Edges are stored as three parallel arrays
 * ({@code u}, {@code v}, {@code w}) so that edge {@code i} connects {@code u[i]}
 * and {@code v[i]} with length/weight {@code w[i]} (positive). The weight is
 * interpreted as the edge <em>length</em> for shortest-path transport costs; the
 * Ollivier-Ricci measure itself is count-based (uniform over neighbors, see
 * {@link OllivierRicciCalculator}).
 *
 * <p>Deterministic by construction: plain arrays, no hash-based iteration order
 * affects any downstream computation.
 *
 * @see OllivierRicciCalculator
 * @see CurriculumOrderer
 */
public record Graph(int n, int[] u, int[] v, double[] w) {

    /**
     * Canonical constructor with validation (SPEC-003 INV-2 reproducibility:
     * reject malformed graphs early rather than produce garbage downstream).
     */
    public Graph {
        if (n < 0) {
            throw new IllegalArgumentException("n must be >= 0, got " + n);
        }
        Objects.requireNonNull(u, "u");
        Objects.requireNonNull(v, "v");
        Objects.requireNonNull(w, "w");
        if (u.length != v.length || u.length != w.length) {
            throw new IllegalArgumentException("u, v, w must have equal length");
        }
        for (int i = 0; i < u.length; i++) {
            if (u[i] < 0 || u[i] >= n || v[i] < 0 || v[i] >= n) {
                throw new IllegalArgumentException("edge " + i + " endpoint out of range: "
                        + u[i] + "," + v[i] + " (n=" + n + ")");
            }
            if (u[i] == v[i]) {
                throw new IllegalArgumentException("self-loop not allowed at edge " + i);
            }
            if (!(w[i] > 0.0)) {
                throw new IllegalArgumentException("edge " + i + " weight must be > 0, got " + w[i]);
            }
        }
        // Defensive copies so the record is truly immutable.
        u = u.clone();
        v = v.clone();
        w = w.clone();
    }

    /** Number of edges. */
    public int edgeCount() {
        return u.length;
    }

    /**
     * Build a graph from a symmetric adjacency matrix. Entry {@code a[i][j] > 0}
     * means an edge between {@code i} and {@code j} of weight {@code a[i][j]};
     * non-positive entries are treated as "no edge". The diagonal is ignored.
     *
     * @param adjacency square {@code n x n} matrix of non-negative edge lengths
     */
    public static Graph of(double[][] adjacency) {
        int n = adjacency.length;
        java.util.ArrayList<Integer> us = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> vs = new java.util.ArrayList<>();
        java.util.ArrayList<Double> ws = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (adjacency[i].length != n) {
                throw new IllegalArgumentException("adjacency must be square");
            }
            for (int j = i + 1; j < n; j++) {
                if (adjacency[i][j] > 0.0) {
                    us.add(i);
                    vs.add(j);
                    ws.add(adjacency[i][j]);
                }
            }
        }
        int m = us.size();
        int[] u = new int[m];
        int[] v = new int[m];
        double[] w = new double[m];
        for (int i = 0; i < m; i++) {
            u[i] = us.get(i);
            v[i] = vs.get(i);
            w[i] = ws.get(i);
        }
        return new Graph(n, u, v, w);
    }
}
