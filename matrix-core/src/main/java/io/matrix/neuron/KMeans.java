package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 423 — Single-cell K-means clustering.
 * <p>Iterative Lloyd's algorithm: assign each point to the nearest centroid
 * (Euclidean), then move centroids to the mean of assigned points.
 * Pure function with caller-supplied {@link Random} for tie-breaking.
 * CONSTITUTION I-safe.
 */
public final class KMeans {

    private KMeans() {}

    public record Result(double[][] centroids, int[] assignments, double totalInertia) {}

    /**
     * @param points k×d matrix (one point per row)
     * @param k      number of clusters
     * @param maxIters max Lloyd iterations
     * @param tol    convergence tolerance on centroid movement
     * @param rng    deterministic RNG for tie-breaking and random init
     */
    public static Result cluster(double[][] points, int k, int maxIters, double tol, Random rng) {
        int n = points.length;
        if (n == 0) return new Result(new double[k][0], new int[0], 0.0);
        int d = points[0].length;
        double[][] centroids = new double[k][d];
        // Init: pick k random distinct points via Fisher-Yates shuffle trick
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        for (int i = 0; i < Math.min(k, n); i++) {
            int pick = i + rng.nextInt(Math.max(1, n - i));
            int tmp = idx[i]; idx[i] = idx[pick]; idx[pick] = tmp;
            System.arraycopy(points[idx[i]], 0, centroids[i], 0, d);
        }
        int[] assign = new int[n];
        int[] counts = new int[k];
        double inertia = 0.0;

        for (int iter = 0; iter < maxIters; iter++) {
            // Assignment step
            double newInertia = 0.0;
            for (int p = 0; p < n; p++) {
                double best = Double.POSITIVE_INFINITY;
                int bestC = 0;
                for (int c = 0; c < k; c++) {
                    double d2 = sqDist(points[p], centroids[c]);
                    if (d2 < best) { best = d2; bestC = c; }
                }
                assign[p] = bestC;
                newInertia += best;
            }
            // Update centroids
            double[][] newCentroids = new double[k][d];
            for (int p = 0; p < n; p++) {
                int c = assign[p];
                counts[c]++;
                for (int j = 0; j < d; j++) newCentroids[c][j] += points[p][j];
            }
            // Update + compute centroid shift
            double maxShift = 0.0;
            for (int c = 0; c < k; c++) {
                if (counts[c] == 0) continue;  // empty cluster — leave at old location
                for (int j = 0; j < d; j++) newCentroids[c][j] /= counts[c];
                double shift = Math.sqrt(sqDist(newCentroids[c], centroids[c]));
                if (shift > maxShift) maxShift = shift;
            }
            centroids = newCentroids;
            counts = new int[k];
            inertia = newInertia;
            if (maxShift < tol) break;
        }
        return new Result(centroids, assign, inertia);
    }

    private static double sqDist(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) { double d = a[i] - b[i]; s += d * d; }
        return s;
    }
}
