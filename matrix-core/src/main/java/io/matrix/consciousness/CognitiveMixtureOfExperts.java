package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * W222 — Cognitive Mixture-of-Experts (MoE).
 *
 * <p>Inspired by MoE architecture in modern LLMs (Gemma 4 26B, Mixtral,
 * etc.). Different "expert" measurement subsystems handle different
 * types of cognitive input. A learned router assigns each profile to
 * top-K experts.
 *
 * <p>Architecture:
 * - N expert networks (in MATRIX: N measurement subsystems)
 * - Router: for each profile, score experts and pick top K
 * - Combine: weighted average of expert outputs
 *
 * <p>Benefits: 3-7x cheaper inference at equivalent quality (Fedus et al.
 * 2022).
 *
 * <p>CONSTITUTION VI compliance: routed cognitive processing, not
 * phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveMixtureOfExperts {

    private CognitiveMixtureOfExperts() {}

    /** Expert ID. */
    public record Expert(String name) {}

    /** MoE result: which experts were used + their outputs. */
    public record MoEResult(
        int[] selectedExperts,
        double[] weights,
        double[] aggregatedOutput
    ) {}

    /**
     * Route profile to top-K experts based on profile features.
     * Router = linear projection + softmax over expert scores.
     *
     * @param profile cognitive profile to route
     * @param experts list of available experts
     * @param topK number of experts to select
     * @param seed RNG seed
     * @return MoE result with selected experts and aggregated output
     */
    public static MoEResult route(CognitiveGenesisProfile profile,
                                    List<Expert> experts,
                                    int topK,
                                    long seed) {
        if (profile == null || experts == null || experts.isEmpty() || topK < 1) {
            return new MoEResult(new int[0], new double[0], new double[0]);
        }
        int nExperts = experts.size();
        // Router: hash profile + expert name → score
        Random rng = new Random(seed);
        double[] scores = new double[nExperts];
        double[] profileFeatures = extractFeatures(profile);
        for (int e = 0; e < nExperts; e++) {
            double score = 0;
            for (int f = 0; f < profileFeatures.length; f++) {
                score += profileFeatures[f] * rng.nextGaussian();
            }
            scores[e] = score;
        }
        // Softmax
        double max = scores[0];
        for (double s : scores) if (s > max) max = s;
        double sum = 0;
        for (int e = 0; e < nExperts; e++) {
            scores[e] = Math.exp(scores[e] - max);
            sum += scores[e];
        }
        for (int e = 0; e < nExperts; e++) scores[e] /= sum;
        // Top K
        Integer[] indices = new Integer[nExperts];
        for (int i = 0; i < nExperts; i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> Double.compare(scores[b], scores[a]));
        int k = Math.min(topK, nExperts);
        int[] selected = new int[k];
        double[] weights = new double[k];
        for (int i = 0; i < k; i++) {
            selected[i] = indices[i];
            weights[i] = scores[indices[i]];
        }
        // Renormalize
        double wsum = 0;
        for (double w : weights) wsum += w;
        for (int i = 0; i < k; i++) weights[i] /= wsum;
        // Aggregated output: weighted sum of profile fields
        double[] aggregated = new double[profileFeatures.length];
        for (int i = 0; i < k; i++) {
            double w = weights[i];
            for (int f = 0; f < aggregated.length; f++) {
                // Each expert perturbs the profile slightly (in real MoE, FFN
                // produces new values). For our purposes, expert = identity
                // with optional noise
                aggregated[f] += w * profileFeatures[f];
            }
        }
        return new MoEResult(selected, weights, aggregated);
    }

    /**
     * Compute routing entropy (lower = more concentrated, higher = uniform).
     */
    public static double routingEntropy(double[] weights) {
        if (weights == null || weights.length == 0) return 0.0;
        double h = 0;
        for (double w : weights) {
            if (w > 0) h -= w * Math.log(w);
        }
        return h / Math.log(weights.length);
    }

    /**
     * Classify routing: CONCENTRATED, BALANCED, or SPARSE.
     */
    public static String classifyRouting(double[] weights) {
        double entropy = routingEntropy(weights);
        if (entropy < 0.3) return "CONCENTRATED";
        if (entropy > 0.8) return "UNIFORM";
        return "BALANCED";
    }

    private static double[] extractFeatures(CognitiveGenesisProfile p) {
        return new double[] {
            p.phiBinary(), p.phiF(), p.phiR(), p.phiLinGauss(),
            p.interAgentPhi(), p.stabilityPhi(), p.crossLevelPhi(),
            Math.min(1.0, p.kolmogorovK() / 100.0),
            p.analogicalSimilarity(), p.conceptualExclusion(),
            p.nkEdgeOfChaosK() / 8.0, p.memristorConductance(),
            Math.min(1.0, p.lSystemComplexityRatio() / 5.0)
        };
    }
}
