package io.matrix.neuron;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * RUN 475 — HermeneuticLoop: Gadamer horizon-merge via HDC voting (DESIGN-60).
 *
 * <p>Implements Gadamer's "fusion of horizons" (Horizontverschmelzung) as
 * an engineering algorithm: multiple perspectives (interpretations) of
 * the same phenomenon are merged via HDC majority voting.
 *
 * <h2>Algorithm</h2>
 * <pre>
 *   1. Each "interpretation" contributes an HDC code representing its view
 *   2. Per-bit: take majority vote across interpretations
 *   3. The merged HDC code represents the "horizon-fused" interpretation
 *   4. Decode to text via nearest-neighbor in codebook
 * </pre>
 *
 * <h2>Why this matters</h2>
 * <p>In MultiBrainEnsemble (8 models), each model gives a different answer.
 * HermeneuticLoop fuses them into a consensus that respects the validity
 * of each interpretation while creating a unified meaning.
 *
 * <h2>CONSTITUTION I</h2>
 * Pure functions. No wall-clock. Deterministic.
 */
public final class HermeneuticLoop {

    private HermeneuticLoop() {}

    /**
     * Fuse multiple HDC interpretations via per-bit majority voting.
     *
     * @param interpretations one HDC code per interpretation (length = hdcBits/64)
     * @return fused HDC code representing consensus
     */
    public static long[] fuseHorizons(long[][] interpretations) {
        if (interpretations == null || interpretations.length == 0) {
            throw new IllegalArgumentException("empty interpretations");
        }
        int bits = interpretations[0].length * 64;
        long[] fused = new long[interpretations[0].length];
        // Per-bit majority vote across all interpretations
        for (int wordIdx = 0; wordIdx < fused.length; wordIdx++) {
            int ones = 0;
            int total = interpretations.length;
            for (long[] interp : interpretations) {
                if (interp.length != fused.length) {
                    throw new IllegalArgumentException("interpretation length mismatch");
                }
                ones += Long.bitCount(interp[wordIdx]);
            }
            int zeros = total - ones;
            // Set bit if majority are 1s
            if (ones >= zeros) {
                fused[wordIdx] = -1L; // all 1s
            } else {
                fused[wordIdx] = 0L; // all 0s
            }
        }
        return fused;
    }

    /**
     * Fuse multiple interpretations AND resolve to nearest codebook entry.
     *
     * @param interpretations  HDC codes from different perspectives
     * @param codebook         map from label → HDC code
     * @return label with smallest Hamming distance to fused consensus
     */
    public static String interpret(String[] labels, long[][] codes, long[] query) {
        if (labels == null || codes == null || query == null) {
            throw new IllegalArgumentException("null inputs");
        }
        if (labels.length != codes.length) {
            throw new IllegalArgumentException("labels/codes length mismatch");
        }
        if (codes.length == 0 || query.length == 0) return null;

        // Find nearest label by Hamming distance
        int bestIdx = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < codes.length; i++) {
            int d = hamming(codes[i], query);
            if (d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }
        return labels[bestIdx];
    }

    /**
     * Full HermeneuticLoop cycle: collect interpretations, fuse, interpret.
     *
     * @param interpretations  one HDC code per perspective
     * @param codebook         map from label → code
     * @return HermeneuticResult with fused code, label, and per-perspective distances
     */
    public static HermeneuticResult cycle(
            long[][] interpretations,
            Map<String, long[]> codebook) {
        if (interpretations == null || interpretations.length == 0) {
            throw new IllegalArgumentException("no interpretations");
        }
        if (codebook == null || codebook.isEmpty()) {
            throw new IllegalArgumentException("empty codebook");
        }
        // 1. Fuse via majority voting
        long[] fused = fuseHorizons(interpretations);

        // 2. Interpret via nearest in codebook
        String[] labels = codebook.keySet().toArray(new String[0]);
        long[][] codes = new long[labels.length][];
        for (int i = 0; i < labels.length; i++) {
            codes[i] = codebook.get(labels[i]);
        }
        String bestLabel = interpret(labels, codes, fused);
        int bestDist = hamming(codes[java.util.Arrays.asList(labels).indexOf(bestLabel)], fused);

        // Per-perspective distances
        int[] perInterp = new int[interpretations.length];
        for (int i = 0; i < interpretations.length; i++) {
            perInterp[i] = hamming(interpretations[i], fused);
        }

        return new HermeneuticResult(fused, bestLabel, bestDist, perInterp);
    }

    /**
     * Hamming distance between two HDC codes.
     */
    public static int hamming(long[] a, long[] b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        if (a.length != b.length) return Integer.MAX_VALUE;
        int dist = 0;
        for (int i = 0; i < a.length; i++) {
            dist += Long.bitCount(a[i] ^ b[i]);
        }
        return dist;
    }

    /**
     * Result of a HermeneuticLoop cycle.
     */
    public record HermeneuticResult(
            long[] fusedCode,
            String consensusLabel,
            int consensusDistance,
            int[] perInterpretationDistances) {
        public double averageDistance() {
            if (perInterpretationDistances == null || perInterpretationDistances.length == 0) {
                return 0.0;
            }
            double sum = 0;
            for (int d : perInterpretationDistances) sum += d;
            return sum / perInterpretationDistances.length;
        }
    }
}
