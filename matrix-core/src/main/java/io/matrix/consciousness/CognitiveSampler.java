package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * W248 — Cognitive Sampler (temperature, top-k, top-p).
 *
 * <p>Inspired by LLM sampling strategies (Holtzman et al. 2020).
 * Sample from a probability distribution over cognitive profiles.
 *
 * <p>Strategies:
 * - Temperature: T -> 0 greedy, T -> ∞ uniform
 * - Top-K: keep only K highest-probability candidates
 * - Top-P (nucleus): keep smallest set summing to ≥ P
 *
 * <p>CONSTITUTION VI compliance: cognitive profile sampling, not
 * phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveSampler {

    private CognitiveSampler() {}

    /**
     * Apply temperature scaling to logits.
     */
    public static double[] temperatureScale(double[] logits, double temperature) {
        if (logits == null) return null;
        if (temperature <= 0) temperature = 1e-9;
        double[] scaled = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            scaled[i] = logits[i] / temperature;
        }
        return scaled;
    }

    /**
     * Softmax: convert logits to probabilities.
     */
    public static double[] softmax(double[] logits) {
        if (logits == null || logits.length == 0) return new double[0];
        double max = Double.NEGATIVE_INFINITY;
        for (double v : logits) if (v > max) max = v;
        double[] probs = new double[logits.length];
        double sum = 0;
        for (int i = 0; i < logits.length; i++) {
            probs[i] = Math.exp(logits[i] - max);
            sum += probs[i];
        }
        for (int i = 0; i < logits.length; i++) probs[i] /= sum;
        return probs;
    }

    /**
     * Top-K filtering: keep only top K probabilities.
     */
    public static double[] topK(double[] probs, int k) {
        if (probs == null) return null;
        if (k <= 0) return new double[probs.length];
        int n = probs.length;
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> Double.compare(probs[b], probs[a]));
        double[] filtered = new double[n];
        int keep = Math.min(k, n);
        double sum = 0;
        for (int i = 0; i < keep; i++) sum += probs[indices[i]];
        if (sum > 0) {
            for (int i = 0; i < keep; i++) {
                filtered[indices[i]] = probs[indices[i]] / sum;
            }
        }
        return filtered;
    }

    /**
     * Top-P (nucleus) filtering: keep smallest set summing to ≥ p.
     */
    public static double[] topP(double[] probs, double p) {
        if (probs == null) return null;
        if (p <= 0) return new double[probs.length];
        if (p >= 1) return probs.clone();
        int n = probs.length;
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> Double.compare(probs[b], probs[a]));
        double[] filtered = new double[n];
        double cumSum = 0;
        int cutoff = n;
        for (int i = 0; i < n; i++) {
            cumSum += probs[indices[i]];
            if (cumSum >= p) {
                cutoff = i + 1;
                break;
            }
        }
        double sum = 0;
        for (int i = 0; i < cutoff; i++) sum += probs[indices[i]];
        if (sum > 0) {
            for (int i = 0; i < cutoff; i++) {
                filtered[indices[i]] = probs[indices[i]] / sum;
            }
        }
        return filtered;
    }

    /**
     * Sample an index from a probability distribution.
     */
    public static int sample(double[] probs, long seed) {
        if (probs == null || probs.length == 0) return -1;
        Random rng = new Random(seed);
        double r = rng.nextDouble();
        double cumSum = 0;
        for (int i = 0; i < probs.length; i++) {
            cumSum += probs[i];
            if (r < cumSum) return i;
        }
        return probs.length - 1;
    }

    /**
     * Combined temperature + top-K + top-P + sample pipeline.
     */
    public static int samplePipeline(double[] logits, double temperature, int k,
                                       double p, long seed) {
        double[] scaled = temperatureScale(logits, temperature);
        double[] probs = softmax(scaled);
        probs = topK(probs, k);
        probs = topP(probs, p);
        return sample(probs, seed);
    }
}
