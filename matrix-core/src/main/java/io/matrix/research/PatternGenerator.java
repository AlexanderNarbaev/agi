package io.matrix.research;

import java.util.Random;

/**
 * RUN 479 — PatternGenerator (DESIGN-62).
 *
 * <p>Generates structured observation patterns for empirical validation
 * of MATRIX brain integration. Five pattern types per W76 deep research:
 *
 * <ul>
 *   <li><b>Periodic</b>: sinusoidal (mimics sensory stimuli)</li>
 *   <li><b>Sparse</b>: ~3% of bits set (matches BitNet b1.58 + biological firing)</li>
 *   <li><b>Recurrent</b>: prev + delta (mimics memory consolidation)</li>
 *   <li><b>Hierarchical</b>: clusters (mimics concepts / Spelke core knowledge)</li>
 *   <li><b>Gaussian</b>: random noise (baseline)</li>
 * </ul>
 */
public final class PatternGenerator {

    public enum Type {
        PERIODIC, SPARSE, RECURRENT, HIERARCHICAL, GAUSSIAN
    }

    private PatternGenerator() {}

    /**
     * Generate a periodic (sinusoidal) pattern.
     * p[i] = amp * sin(2π * freq * i / dims + phase)
     */
    public static float[] periodic(int dims, double freq, double phase,
                                       double amp, Random rng) {
        if (dims < 1) throw new IllegalArgumentException("dims < 1");
        float[] p = new float[dims];
        for (int i = 0; i < dims; i++) {
            p[i] = (float) (amp * Math.sin(2 * Math.PI * freq * i / dims + phase));
        }
        return p;
    }

    /**
     * Generate a sparse pattern with specified density.
     * Independently set each dim to +1 with probability density.
     */
    public static float[] sparse(int dims, double density, Random rng) {
        if (dims < 1) throw new IllegalArgumentException("dims < 1");
        if (density < 0 || density > 1) throw new IllegalArgumentException("density in [0, 1]");
        float[] p = new float[dims];
        for (int i = 0; i < dims; i++) {
            p[i] = rng.nextDouble() < density ? 1.0f : -1.0f;
        }
        return p;
    }

    /**
     * Recurrent pattern: prev + delta. If prev is null, generates fresh sparse.
     */
    public static float[] recurrent(float[] prev, float delta, Random rng) {
        if (prev == null) return sparse(1024, 0.03, rng);
        if (delta < 0 || delta > 1) throw new IllegalArgumentException("delta in [0, 1]");
        int dims = prev.length;
        float[] p = new float[dims];
        for (int i = 0; i < dims; i++) {
            float change = (float) (delta * rng.nextGaussian());
            p[i] = prev[i] + change;
        }
        return p;
    }

    /**
     * Hierarchical pattern with nClusters clusters.
     * Each cluster has its own center value (spaced apart for distinctness).
     * Within cluster: small noise around center.
     */
    public static float[] hierarchical(int dims, int nClusters, int clusterSize,
                                          Random rng) {
        if (dims < 1) throw new IllegalArgumentException("dims < 1");
        if (nClusters < 1) throw new IllegalArgumentException("nClusters < 1");
        if (clusterSize < 1) throw new IllegalArgumentException("clusterSize < 1");
        // Each contiguous block of clusterSize dims belongs to one cluster
        int[] clusterOfDim = new int[dims];
        for (int i = 0; i < dims; i++) {
            clusterOfDim[i] = Math.min(i / clusterSize, nClusters - 1);
        }
        // Cluster centers: well-separated
        float[] centers = new float[nClusters];
        for (int c = 0; c < nClusters; c++) {
            centers[c] = (c - nClusters / 2.0f) * 2.0f;
        }
        float[] p = new float[dims];
        for (int i = 0; i < dims; i++) {
            float noise = (float) (0.05 * rng.nextGaussian());
            p[i] = centers[clusterOfDim[i]] + noise;
        }
        return p;
    }

    /**
     * Gaussian random pattern (baseline / noise floor).
     */
    public static float[] gaussian(int dims, double mean, double std, Random rng) {
        if (dims < 1) throw new IllegalArgumentException("dims < 1");
        if (std < 0) throw new IllegalArgumentException("std < 0");
        float[] p = new float[dims];
        for (int i = 0; i < dims; i++) {
            p[i] = (float) (mean + std * rng.nextGaussian());
        }
        return p;
    }

    /**
     * Generate a pattern of the specified type.
     */
    public static float[] generate(Type type, int dims, Random rng) {
        if (type == null) throw new IllegalArgumentException("null type");
        switch (type) {
            case PERIODIC: return periodic(dims, 2.0, 0.0, 1.0, rng);
            case SPARSE: return sparse(dims, 0.03, rng);
            case RECURRENT: return sparse(dims, 0.03, rng);
            case HIERARCHICAL: return hierarchical(dims, 4, 32, rng);
            case GAUSSIAN: return gaussian(dims, 0.0, 0.1, rng);
            default: throw new IllegalArgumentException("unsupported type: " + type);
        }
    }

    /**
     * Generate a pattern trajectory.
     */
    public static float[][] generateTrajectory(Type type, int dims, int length,
                                                 float delta, Random rng) {
        if (length < 1) throw new IllegalArgumentException("length < 1");
        float[][] traj = new float[length][];
        float[] current = generate(type, dims, rng);
        traj[0] = current;
        for (int t = 1; t < length; t++) {
            if (type == Type.RECURRENT) {
                traj[t] = recurrent(current, delta, rng);
            } else {
                traj[t] = new float[dims];
                for (int i = 0; i < dims; i++) {
                    traj[t][i] = (float) (current[i] + 0.05 * rng.nextGaussian());
                }
            }
            current = traj[t];
        }
        return traj;
    }
}
