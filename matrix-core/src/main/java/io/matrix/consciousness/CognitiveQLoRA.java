package io.matrix.consciousness;

import java.util.Random;

/**
 * W325 — Cognitive QLoRA (Quantized Low-Rank Adaptation).
 *
 * <p>Inspired by QLoRA (Dettmers et al. 2023). 4-bit quantized LoRA
 * for memory-efficient cognitive fine-tuning.
 *
 * <p>Process:
 * 1. Quantize base weights to 4-bit (NF4)
 * 2. Add LoRA adapters (low-rank)
 * 3. Dequantize on-the-fly during forward pass
 *
 * <p>CONSTITUTION VI compliance: quantized cognitive adaptation,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveQLoRA {

    private CognitiveQLoRA() {}

    /** Quantized weights. */
    public record QuantizedWeights(int[][] data, double scale, double zeroPoint) {}

    /** QLoRA result. */
    public record QLoRAResult(
        double memoryReduction,  // vs full precision
        double approximationError
    ) {}

    private int teacherDim;
    private int studentDim;
    private long seed;
    private double[] baseWeights; // Normal Float 4 quantized
    private double[][] loraA;    // LoRA adapter A
    private double[][] loraB;    // LoRA adapter B

    public CognitiveQLoRA(int teacherDim, int studentDim, long seed) {
        if (teacherDim < 1 || studentDim < 1 || studentDim > teacherDim) {
            throw new IllegalArgumentException("invalid dims");
        }
        this.teacherDim = teacherDim;
        this.studentDim = studentDim;
        this.seed = seed;
        Random rng = new Random(seed);
        // Initialize weights with small values
        this.baseWeights = new double[teacherDim];
        for (int i = 0; i < teacherDim; i++) {
            baseWeights[i] = rng.nextGaussian() * 0.01;
        }
        double std = 1.0 / Math.sqrt((double) teacherDim);
        this.loraA = new double[teacherDim][studentDim];
        this.loraB = new double[studentDim][teacherDim];
        for (int i = 0; i < teacherDim; i++) {
            for (int j = 0; j < studentDim; j++) {
                loraA[i][j] = rng.nextGaussian() * std;
            }
        }
    }

    /**
     * Quantize a vector to 4-bit representation.
     * Uses NF4-style quantization (normalized float 4-bit).
     */
    public QuantizedWeights quantize(double[] weights) {
        if (weights == null || weights.length != teacherDim) return null;
        // Find min/max for quantization
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double w : weights) {
            if (w < min) min = w;
            if (w > max) max = w;
        }
        double scale = (max - min) / 15.0;
        double zeroPoint = -min / scale;
        int[][] data = new int[1][teacherDim];
        for (int i = 0; i < teacherDim; i++) {
            double normalized = (weights[i] - min) / scale;
            data[0][i] = (int) Math.max(0, Math.min(15, Math.round(normalized)));
        }
        return new QuantizedWeights(data, scale, zeroPoint);
    }

    /**
     * Apply QLoRA: dequantize base + LoRA delta.
     */
    public double[] apply(double[] input, QuantizedWeights weights) {
        if (input == null || input.length != teacherDim) return null;
        if (weights == null) return input;
        // Dequantize
        double[] dequantized = new double[teacherDim];
        for (int i = 0; i < teacherDim; i++) {
            int quantized = weights.data()[0][i];
            dequantized[i] = quantized * weights.scale() - weights.zeroPoint() * weights.scale();
        }
        // Apply LoRA: y = x + B(Ax)
        double[] result = new double[teacherDim];
        double[] loraOut = new double[studentDim];
        for (int j = 0; j < studentDim; j++) {
            double sum = 0;
            for (int i = 0; i < teacherDim; i++) {
                sum += input[i] * loraA[i][j];
            }
            loraOut[j] = sum;
        }
        for (int i = 0; i < teacherDim; i++) {
            double sum = 0;
            for (int j = 0; j < studentDim; j++) {
                sum += loraOut[j] * loraB[j][i];
            }
            result[i] = dequantized[i] + sum;
        }
        return result;
    }

    /**
     * Compute memory reduction vs full precision.
     * Full: 32 bits/weight; QLoRA: 4 bits + LoRA overhead
     */
    public double memoryReduction() {
        // LoRA params: 2 × teacherDim × studentDim × 32 bits
        long loraBits = 2L * teacherDim * studentDim * 32L;
        // QLoRA base: teacherDim × 4 bits
        long qloraBits = teacherDim * 4L;
        // Full: teacherDim × 32 bits
        long fullBits = teacherDim * 32L;
        return 1.0 - ((double)(loraBits + qloraBits) / fullBits);
    }

    /**
     * Compute approximation error from quantization.
     */
    public double approximationError(double[] original, double[] reconstructed) {
        if (original == null || reconstructed == null ||
                original.length != reconstructed.length) return 1.0;
        double sum = 0;
        for (int i = 0; i < original.length; i++) {
            double d = original[i] - reconstructed[i];
            sum += d * d;
        }
        return Math.sqrt(sum / original.length);
    }

    public int teacherDim() { return teacherDim; }
    public int studentDim() { return studentDim; }
    public long seed() { return seed; }
}
