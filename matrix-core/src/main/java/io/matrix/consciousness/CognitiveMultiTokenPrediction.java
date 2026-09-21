package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * W305 — Cognitive Multi-Token Prediction.
 *
 * <p>Inspired by Multi-Token Prediction (Meta, 2024) and DeepSeek-V3
 * (2024). Predict multiple next cognitive profiles in parallel.
 *
 * <p>Use cases:
 * - Speculative cognitive state prediction
 * - Parallel branch exploration
 * - Faster cognitive rollout
 *
 * <p>CONSTITUTION VI compliance: multi-token cognitive prediction,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveMultiTokenPrediction {

    private CognitiveMultiTokenPrediction() {}

    /** Multi-token prediction result. */
    public record MultiTokenPrediction(
        CognitiveGenesisProfile input,
        List<CognitiveGenesisProfile> predictions,
        List<Double> confidenceScores,
        double averageConfidence
    ) {}

    /**
     * Predict k next cognitive profiles.
     */
    public static MultiTokenPrediction predict(CognitiveGenesisProfile input,
                                                  int k,
                                                  long seed) {
        if (input == null || k < 1) {
            return new MultiTokenPrediction(input, new ArrayList<>(), new ArrayList<>(), 0.0);
        }
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> predictions = new ArrayList<>();
        List<Double> confidenceScores = new ArrayList<>();
        for (int i = 1; i <= k; i++) {
            CognitiveGenesisProfile pred = perturb(input, rng, i);
            predictions.add(pred);
            // Confidence decays with distance
            double conf = Math.max(0.0, 1.0 - 0.15 * i + rng.nextGaussian() * 0.05);
            confidenceScores.add(conf);
        }
        double avgConf = confidenceScores.stream()
            .mapToDouble(Double::doubleValue).average().orElse(0.0);
        return new MultiTokenPrediction(input, predictions, confidenceScores, avgConf);
    }

    /**
     * Verify predictions using a verifier (e.g., Constitutional AI).
     */
    public static List<CognitiveGenesisProfile> verify(List<CognitiveGenesisProfile> predictions,
                                                         java.util.function.Predicate<CognitiveGenesisProfile> verifier) {
        if (predictions == null) return new ArrayList<>();
        List<CognitiveGenesisProfile> accepted = new ArrayList<>();
        for (CognitiveGenesisProfile p : predictions) {
            if (verifier == null || verifier.test(p)) {
                accepted.add(p);
            }
        }
        return accepted;
    }

    /**
     * Acceptance rate: % of predictions that pass verification.
     */
    public static double acceptanceRate(int totalPredictions, int acceptedPredictions) {
        if (totalPredictions <= 0) return 0.0;
        return (double) acceptedPredictions / totalPredictions;
    }

    private static CognitiveGenesisProfile perturb(CognitiveGenesisProfile p, Random rng, int step) {
        double noise = 0.05 * step * (rng.nextGaussian());
        double newPhi = Math.max(0.0, Math.min(1.0, p.phiBinary() + noise));
        return new CognitiveGenesisProfile(
            newPhi, p.phiF(), p.phiR(), p.phiLinGauss(),
            p.interAgentPhi(), p.stabilityPhi(), p.crossLevelPhi(),
            p.kolmogorovK(),
            p.analogicalSimilarity(), p.conceptualExclusion(),
            p.nkEdgeOfChaosK(),
            p.memristorConductance(), p.lSystemComplexityRatio()
        );
    }
}
