package io.matrix.consciousness;

import java.util.Random;

/**
 * W221 — Cognitive Quantization (FP4-style profile compression).
 *
 * <p>Inspired by FP4/FP8 quantization (NVIDIA B200). Compress cognitive
 * profiles to 4-bit or 8-bit representations to save memory.
 *
 * <p>Strategy:
 * - Find min/max per field
 * - Scale to [0, 2^N - 1]
 * - Round to nearest integer (quantize)
 * - Store as integer (dequantize on retrieval)
 *
 * <p>Use cases:
 * - Compress profile history (4x memory savings at 4-bit)
 * - Fast approximate similarity (integer ops)
 * - Memory-bounded cognitive state
 *
 * <p>CONSTITUTION VI compliance: quantized cognitive state, not
 * phenomenal consciousness claim.
 */
public final class CognitiveQuantization {

    private CognitiveQuantization() {}

    /** Quantized profile: 13 short values (4 or 8 bit). */
    public record QuantizedProfile(short[] values) {}

    /**
     * Quantize a profile to 4-bit representation.
     * Each field: 0..15 mapped from original range.
     */
    public static QuantizedProfile quantize4Bit(CognitiveGenesisProfile profile) {
        if (profile == null) return new QuantizedProfile(new short[13]);
        short[] result = new short[13];
        double[] fields = extractFields(profile);
        for (int i = 0; i < fields.length; i++) {
            double min = FIELD_MINS[i];
            double max = FIELD_MAXES[i];
            double normalized = (fields[i] - min) / (max - min);
            normalized = Math.max(0.0, Math.min(1.0, normalized));
            result[i] = (short) Math.round(normalized * 15);
        }
        return new QuantizedProfile(result);
    }

    /**
     * Quantize to 8-bit (0..255).
     */
    public static QuantizedProfile quantize8Bit(CognitiveGenesisProfile profile) {
        if (profile == null) return new QuantizedProfile(new short[13]);
        short[] result = new short[13];
        double[] fields = extractFields(profile);
        for (int i = 0; i < fields.length; i++) {
            double min = FIELD_MINS[i];
            double max = FIELD_MAXES[i];
            double normalized = (fields[i] - min) / (max - min);
            normalized = Math.max(0.0, Math.min(1.0, normalized));
            result[i] = (short) Math.round(normalized * 255);
        }
        return new QuantizedProfile(result);
    }

    /**
     * Dequantize 4-bit back to profile.
     */
    public static CognitiveGenesisProfile dequantize4Bit(QuantizedProfile qp) {
        if (qp == null || qp.values().length != 13) return null;
        double[] fields = new double[13];
        for (int i = 0; i < 13; i++) {
            fields[i] = FIELD_MINS[i] + (qp.values()[i] / 15.0) * (FIELD_MAXES[i] - FIELD_MINS[i]);
        }
        return buildProfile(fields);
    }

    /**
     * Dequantize 8-bit back to profile.
     */
    public static CognitiveGenesisProfile dequantize8Bit(QuantizedProfile qp) {
        if (qp == null || qp.values().length != 13) return null;
        double[] fields = new double[13];
        for (int i = 0; i < 13; i++) {
            fields[i] = FIELD_MINS[i] + (qp.values()[i] / 255.0) * (FIELD_MAXES[i] - FIELD_MINS[i]);
        }
        return buildProfile(fields);
    }

    /**
     * Quantization error: MSE between original and dequantized.
     */
    public static double quantizationError(CognitiveGenesisProfile original, CognitiveGenesisProfile reconstructed) {
        if (original == null || reconstructed == null) return 0.0;
        double[] o = extractFields(original);
        double[] r = extractFields(reconstructed);
        double sum = 0;
        for (int i = 0; i < o.length; i++) {
            double d = o[i] - r[i];
            sum += d * d;
        }
        return sum / o.length;
    }

    /**
     * Compute compression ratio: 4-bit = 4x, 8-bit = 2x.
     */
    public static double compressionRatio(int bits) {
        return 32.0 / bits;
    }

    private static double[] extractFields(CognitiveGenesisProfile p) {
        return new double[] {
            p.phiBinary(), p.phiF(), p.phiR(), p.phiLinGauss(),
            p.interAgentPhi(), p.stabilityPhi(), p.crossLevelPhi(),
            p.kolmogorovK(), p.analogicalSimilarity(), p.conceptualExclusion(),
            p.nkEdgeOfChaosK(), p.memristorConductance(), p.lSystemComplexityRatio()
        };
    }

    private static CognitiveGenesisProfile buildProfile(double[] fields) {
        return new CognitiveGenesisProfile(
            clamp01(fields[0]), fields[1], fields[2], fields[3],
            fields[4], fields[5], fields[6],
            Math.max(0, fields[7]),
            clamp01(fields[8]), clamp01(fields[9]),
            (int) Math.max(0, Math.min(8, fields[10])),
            clamp01(fields[11]), clamp01(fields[12])
        );
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // Per-field ranges (approximate)
    private static final double[] FIELD_MINS = {
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0, 0.0, 0.0
    };
    private static final double[] FIELD_MAXES = {
        1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
        100.0, 1.0, 1.0, 8, 1.0, 5.0
    };
}
