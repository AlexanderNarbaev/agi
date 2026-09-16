package io.matrix.consciousness;

import java.util.Random;

/**
 * W252 — Cognitive Distillation (smaller student model).
 *
 * <p>Inspired by knowledge distillation (Hinton et al. 2015). Train
 * a smaller "student" model to mimic a larger "teacher" model.
 *
 * <p>For cognitive profiles: project to lower-dim space.
 *
 * <p>CONSTITUTION VI compliance: distilled cognitive state, not
 * phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveDistillation {

    private final int teacherDim;
    private final int studentDim;
    private final long seed;
    private final double[][] projection; // [teacherDim × studentDim]

    public CognitiveDistillation(int teacherDim, int studentDim, long seed) {
        if (teacherDim < 1 || studentDim < 1) {
            throw new IllegalArgumentException("dims must be >= 1");
        }
        this.teacherDim = teacherDim;
        this.studentDim = studentDim;
        this.seed = seed;
        Random rng = new Random(seed);
        double std = 1.0 / Math.sqrt((double) teacherDim);
        this.projection = new double[teacherDim][studentDim];
        for (int i = 0; i < teacherDim; i++) {
            for (int j = 0; j < studentDim; j++) {
                projection[i][j] = rng.nextGaussian() * std;
            }
        }
    }

    /** Distill teacher vector to student space. */
    public double[] distill(double[] teacherVec) {
        if (teacherVec == null || teacherVec.length != teacherDim) return null;
        double[] result = new double[studentDim];
        for (int j = 0; j < studentDim; j++) {
            double sum = 0;
            for (int i = 0; i < teacherDim; i++) {
                sum += teacherVec[i] * projection[i][j];
            }
            result[j] = sum;
        }
        return result;
    }

    /** Distill a profile via embedding. */
    public double[] distillProfile(CognitiveGenesisProfile profile) {
        if (profile == null) return null;
        CognitiveEmbedding embedder = new CognitiveEmbedding(teacherDim, seed);
        return distill(embedder.embed(profile));
    }

    /** Compression ratio vs teacher. */
    public double compressionRatio() {
        return (double) teacherDim / studentDim;
    }

    /** Number of parameters in distillation layer. */
    public long parameterCount() {
        return (long) teacherDim * studentDim;
    }

    public int teacherDim() { return teacherDim; }
    public int studentDim() { return studentDim; }
    public long seed() { return seed; }
}
