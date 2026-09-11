package io.matrix.neuron;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * RUN 419 — PageRank: power-iteration on a directed graph.
 * <p>Pure function. Pure operating-on-double-arrays. CONSTITUTION I-safe.
 * <p>The damping factor {@code d=0.85} matches the original Page-and-Brin
 * formulation. If {@code seedRng != null} teleport picks are sampled for
 * tie-breaking; otherwise teleports are uniform (deterministic).
 */
public final class PageRank {

    private PageRank() {}

    /**
     * @param adjacency list of outgoing neighbours per node; dangling nodes
     *                  (no outgoing edges) are handled implicitly (their
     *                  rank is redistributed uniformly).
     * @param damping   the {@code d} factor; default 0.85 (Pages et al.)
     * @param iters     number of power iterations; 50 is usually enough
     * @param tol       early-stop when max(|r_t − r_{t-1}|) < tol
     * @param rng       optional, currently unused; kept for future stochastic PR
     */
    public static double[] rank(List<List<Integer>> adjacency,
                                double damping, int iters, double tol, Random rng) {
        int n = adjacency.size();
        double[] r = new double[n];
        double[] rNext = new double[n];
        Arrays.fill(r, 1.0 / n);
        // Pre-compute out-degree for dangling distribution
        int[] outDegree = new int[n];
        for (int i = 0; i < n; i++) outDegree[i] = adjacency.get(i).size();
        // Teleport mass including dangling
        double[] teleport = new double[n];
        double sharePerNode = 1.0 / n;
        for (int i = 0; i < n; i++) teleport[i] = sharePerNode;

        for (int t = 0; t < iters; t++) {
            double danglingSum = 0.0;
            for (int i = 0; i < n; i++) if (outDegree[i] == 0) danglingSum += r[i];
            Arrays.fill(rNext, (1.0 - damping) * sharePerNode +
                    damping * danglingSum * sharePerNode);
            for (int i = 0; i < n; i++) {
                if (outDegree[i] == 0) continue;
                double share = damping * r[i] / outDegree[i];
                for (int j : adjacency.get(i)) rNext[j] += share;
            }
            double maxDelta = 0.0;
            for (int i = 0; i < n; i++) {
                double d = Math.abs(rNext[i] - r[i]);
                if (d > maxDelta) maxDelta = d;
                r[i] = rNext[i];
            }
            if (maxDelta < tol) break;
        }
        return r;
    }

    /** Convenience: sorted (node, rank) descending. */
    public static List<int[]> topK(double[] ranks, int k) {
        List<int[]> pairs = new ArrayList<>();
        int n = Math.min(k, ranks.length);
        Integer[] idx = new Integer[ranks.length];
        for (int i = 0; i < ranks.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare(ranks[b], ranks[a]));
        for (int i = 0; i < n; i++) pairs.add(new int[]{idx[i], (int) (ranks[idx[i]] * 1e9)});
        return pairs;
    }
}
