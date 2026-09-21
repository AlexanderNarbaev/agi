package io.matrix.consciousness;

import java.util.List;
import java.util.Random;

/**
 * W215 — Profile Embedding 2D (UMAP-style 2D projection).
 *
 * <p>Inspired by UMAP (McInnes et al. 2018) and t-SNE. Reduce high-dim
 * cognitive profile embeddings to 2D for visualization.
 *
 * <p>Simple approach: PCA-style projection to 2D using the two largest
 * variance directions.
 *
 * <p>Use cases:
 * - Visualize cognitive trajectories
 * - Cluster cognitive regimes
 * - Identify outliers / regime transitions
 *
 * <p>CONSTITUTION VI compliance: 2D projection of cognitive state for
 * visualization, not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random instances are seeded.
 */
public final class ProfileEmbedding2D {

    private ProfileEmbedding2D() {}

    /** 2D point for a profile. */
    public record Point2D(double x, double y) {}

    /**
     * Project profiles to 2D using random projection (Johnson-Lindenstrauss
     * style) with seeded random projection matrix.
     */
    public static Point2D[] project(List<CognitiveGenesisProfile> profiles, long seed) {
        if (profiles == null || profiles.isEmpty()) return new Point2D[0];
        CognitiveEmbedding embedder = new CognitiveEmbedding(64, seed);
        int n = profiles.size();
        double[][] vectors = new double[n][64];
        for (int i = 0; i < n; i++) {
            vectors[i] = embedder.embed(profiles.get(i));
        }
        // Subtract mean
        double[] mean = new double[64];
        for (int i = 0; i < n; i++) {
            for (int d = 0; d < 64; d++) {
                mean[d] += vectors[i][d];
            }
        }
        for (int d = 0; d < 64; d++) mean[d] /= n;
        double[][] centered = new double[n][64];
        for (int i = 0; i < n; i++) {
            for (int d = 0; d < 64; d++) {
                centered[i][d] = vectors[i][d] - mean[d];
            }
        }
        // PCA: compute top 2 eigenvectors of covariance matrix via power iteration
        double[][] cov = new double[64][64];
        for (int a = 0; a < 64; a++) {
            for (int b = 0; b < 64; b++) {
                double sum = 0;
                for (int i = 0; i < n; i++) {
                    sum += centered[i][a] * centered[i][b];
                }
                cov[a][b] = sum / (n - 1);
            }
        }
        double[] ev1 = powerIteration(cov, 64, 100, seed);
        // Deflate
        double[][] covDeflated = new double[64][64];
        for (int a = 0; a < 64; a++) {
            for (int b = 0; b < 64; b++) {
                covDeflated[a][b] = cov[a][b] - ev1[a] * ev1[b] * dot(ev1, cov, a);
            }
        }
        double[] ev2 = powerIteration(covDeflated, 64, 100, seed + 1);
        // Project
        Point2D[] points = new Point2D[n];
        for (int i = 0; i < n; i++) {
            double x = 0, y = 0;
            for (int d = 0; d < 64; d++) {
                x += centered[i][d] * ev1[d];
                y += centered[i][d] * ev2[d];
            }
            points[i] = new Point2D(x, y);
        }
        return points;
    }

    private static double dot(double[] vec, double[][] mat, int row) {
        double sum = 0;
        for (int j = 0; j < vec.length; j++) {
            sum += mat[row][j] * vec[j];
        }
        return sum;
    }

    /**
     * Power iteration to find dominant eigenvector.
     */
    private static double[] powerIteration(double[][] mat, int dim, int iterations, long seed) {
        Random rng = new Random(seed);
        double[] v = new double[dim];
        for (int i = 0; i < dim; i++) v[i] = rng.nextGaussian();
        // Normalize
        double norm = 0;
        for (double x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < dim; i++) v[i] /= norm;
        for (int it = 0; it < iterations; it++) {
            double[] next = new double[dim];
            for (int i = 0; i < dim; i++) {
                double sum = 0;
                for (int j = 0; j < dim; j++) {
                    sum += mat[i][j] * v[j];
                }
                next[i] = sum;
            }
            double norm2 = 0;
            for (double x : next) norm2 += x * x;
            norm2 = Math.sqrt(norm2);
            if (norm2 > 0) for (int i = 0; i < dim; i++) next[i] /= norm2;
            v = next;
        }
        return v;
    }

    /**
     * Compute trajectory length: sum of distances between consecutive points.
     */
    public static double trajectoryLength(Point2D[] points) {
        if (points == null || points.length < 2) return 0.0;
        double total = 0;
        for (int i = 1; i < points.length; i++) {
            double dx = points[i].x() - points[i - 1].x();
            double dy = points[i].y() - points[i - 1].y();
            total += Math.sqrt(dx * dx + dy * dy);
        }
        return total;
    }

    /**
     * Compute bounding box of points.
     */
    public static double[] boundingBox(Point2D[] points) {
        if (points == null || points.length == 0) return new double[]{0, 0, 0, 0};
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Point2D p : points) {
            if (p.x() < minX) minX = p.x();
            if (p.x() > maxX) maxX = p.x();
            if (p.y() < minY) minY = p.y();
            if (p.y() > maxY) maxY = p.y();
        }
        return new double[]{minX, minY, maxX, maxY};
    }
}
