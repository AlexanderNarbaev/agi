package io.matrix.consciousness;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * W106 — Conceptual exclusion (Dignāga apoha).
 *
 * <p>Apoha (अपोह, "exclusion" / "differentiation") is the central
 * concept in Dignāga's Pramāṇasamuccaya (~5th century CE). Dignāga,
 * the Buddhist logician, argued that concepts are not defined by
 * intrinsic properties but by mutual exclusion from other concepts.
 *
 * <p>"It is through the exclusion of other things that the meaning
 * of a word is established" — Dignāga, PS 1.5.
 *
 * <p>In MATRIX cognitive architecture, conceptual exclusion measures
 * the differentiation between two concept-vectors. High exclusion =
 * concepts are distinct. Low exclusion = concepts confusable.
 *
 * <p>CONSTITUTION VI compliance: measure of concept distinctness,
 * not a phenomenal consciousness claim.
 */
public final class ConceptualExclusion {

    private ConceptualExclusion() {}

    /**
     * Jaccard exclusion: 1 - Jaccard similarity between two feature sets.
     * Range: [0, 1]. 1 = completely disjoint (high exclusion), 0 = identical.
     *
     * <p>Method: convert trajectories to sets of unique features (states),
     * compute Jaccard, return its complement.
     */
    public static double jaccardExclusion(long[] a, long[] b) {
        if (a == null || b == null) return 0.0;
        Set<Long> setA = new HashSet<>();
        Set<Long> setB = new HashSet<>();
        for (long x : a) {
            if (x != 0) setA.add(x); // 0 = "no feature", ignore
        }
        for (long x : b) {
            if (x != 0) setB.add(x);
        }
        if (setA.isEmpty() && setB.isEmpty()) return 0.0;
        Set<Long> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<Long> union = new HashSet<>(setA);
        union.addAll(setB);
        double jaccard = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        return 1.0 - jaccard;
    }

    /**
     * Bit-mask exclusion: for each long in the trajectories, compute the
     * Hamming distance and return normalized average.
     *
     * <p>Method: pairwise XOR and bit count, averaged over min length.
     */
    public static double bitMaskExclusion(long[] a, long[] b) {
        if (a == null || b == null) return 0.0;
        if (a.length == 0 || b.length == 0) return 0.0;
        int len = Math.min(a.length, b.length);
        long totalDiff = 0;
        for (int i = 0; i < len; i++) {
            totalDiff += Long.bitCount(a[i] ^ b[i]);
        }
        double avgDiffBits = (double) totalDiff / len;
        return avgDiffBits / 64.0; // normalized to [0, 1]
    }

    /**
     * Variance-based exclusion: two concepts are excluded if their
     * distributional variance doesn't overlap. Returns 1.0 when distributions
     * are far apart, 0.0 when identical.
     *
     * <p>Method: compute means and variances; compare with 2-sigma overlap.
     */
    public static double distributionalExclusion(long[] a, long[] b) {
        if (a == null || b == null) return 0.0;
        if (a.length == 0 || b.length == 0) return 0.0;
        double[] statsA = meanVar(a);
        double[] statsB = meanVar(b);
        double meanA = statsA[0], varA = statsA[1];
        double meanB = statsB[0], varB = statsB[1];
        if (varA == 0 && varB == 0) {
            // Both constant; excluded if different constant
            return meanA == meanB ? 0.0 : 1.0;
        }
        // 2-sigma bands
        double lowA = meanA - 2 * Math.sqrt(varA);
        double highA = meanA + 2 * Math.sqrt(varA);
        double lowB = meanB - 2 * Math.sqrt(varB);
        double highB = meanB + 2 * Math.sqrt(varB);
        // Overlap length
        double overlapLow = Math.max(lowA, lowB);
        double overlapHigh = Math.min(highA, highB);
        double overlap = Math.max(0.0, overlapHigh - overlapLow);
        double span = Math.max(highA, highB) - Math.min(lowA, lowB);
        if (span == 0) return 0.0;
        return 1.0 - overlap / span;
    }

    /**
     * Compute a composite exclusion score, weighted combination of all three
     * exclusion metrics. Equal weights for each component.
     */
    public static double compositeExclusion(long[] a, long[] b) {
        double j = jaccardExclusion(a, b);
        double bm = bitMaskExclusion(a, b);
        double dist = distributionalExclusion(a, b);
        return (j + bm + dist) / 3.0;
    }

    private static double[] meanVar(long[] a) {
        double n = a.length;
        double sum = 0;
        for (long x : a) sum += x;
        double mean = sum / n;
        double var = 0;
        for (long x : a) var += (x - mean) * (x - mean);
        return new double[] { mean, var / n };
    }
}
