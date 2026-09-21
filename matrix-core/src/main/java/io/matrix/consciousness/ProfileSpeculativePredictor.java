package io.matrix.consciousness;

import java.util.List;

/**
 * W211 — Profile Speculative Predictor.
 *
 * <p>Inspired by Speculative Decoding (Leviathan et al. 2023, Chen et al.
 * 2023 EAGLE). Draft predictor guesses next cognitive profile based on
 * history. Verifier checks if draft matches actual evolution.
 *
 * <p>Strategy:
 * - Draft: linear extrapolation from last K profiles
 * - Verify: cosine similarity between draft and actual
 * - Accept draft if similarity > threshold
 *
 * <p>Use cases:
 * - Anticipatory cognitive control
 * - Anomaly detection (low acceptance = surprise)
 * - Real-time cognitive monitoring
 *
 * <p>CONSTITUTION VI compliance: speculative prediction of cognitive
 * state, not phenomenal consciousness claim.
 */
public final class ProfileSpeculativePredictor {

    private ProfileSpeculativePredictor() {}

    /** Result of speculative prediction. */
    public record PredictionResult(
        CognitiveGenesisProfile draft,
        double similarity,
        boolean accepted
    ) {}

    /**
     * Generate a draft prediction from the last K profiles (linear
     * extrapolation).
     *
     * @param history recent profile sequence
     * @param k number of recent profiles to use
     * @return predicted next profile
     */
    public static CognitiveGenesisProfile draftPredict(List<CognitiveGenesisProfile> history, int k) {
        if (history == null || history.isEmpty()) return null;
        if (history.size() < k) k = history.size();
        if (k <= 1) return history.get(history.size() - 1);
        // Linear extrapolation: predict delta
        CognitiveGenesisProfile last = history.get(history.size() - 1);
        CognitiveGenesisProfile prev = history.get(history.size() - 2);
        double deltaPhi = last.phiBinary() - prev.phiBinary();
        double predictedPhi = last.phiBinary() + deltaPhi;
        return new CognitiveGenesisProfile(
            Math.max(0.0, Math.min(1.0, predictedPhi)),
            last.phiF() + (last.phiF() - prev.phiF()),
            last.phiR() + (last.phiR() - prev.phiR()),
            last.phiLinGauss() + (last.phiLinGauss() - prev.phiLinGauss()),
            last.interAgentPhi(),
            last.stabilityPhi() + (last.stabilityPhi() - prev.stabilityPhi()),
            last.crossLevelPhi() + (last.crossLevelPhi() - prev.crossLevelPhi()),
            Math.max(0.0, last.kolmogorovK() + (last.kolmogorovK() - prev.kolmogorovK())),
            last.analogicalSimilarity(),
            last.conceptualExclusion(),
            last.nkEdgeOfChaosK(),
            last.memristorConductance(),
            Math.max(0.0, last.lSystemComplexityRatio() + (last.lSystemComplexityRatio() - prev.lSystemComplexityRatio()))
        );
    }

    /**
     * Speculate: generate draft and verify against actual.
     *
     * @param history profile sequence
     * @param actual actual next profile
     * @param k draft window size
     * @param threshold acceptance threshold (cosine similarity)
     * @return prediction result
     */
    public static PredictionResult speculate(List<CognitiveGenesisProfile> history,
                                              CognitiveGenesisProfile actual,
                                              int k,
                                              double threshold) {
        CognitiveGenesisProfile draft = draftPredict(history, k);
        if (draft == null || actual == null) {
            return new PredictionResult(draft, 0.0, false);
        }
        CognitiveEmbedding emb = new CognitiveEmbedding(32, 42L);
        double[] dVec = emb.embed(draft);
        double[] aVec = emb.embed(actual);
        double sim = CognitiveEmbedding.cosineSimilarity(dVec, aVec);
        boolean accepted = sim >= threshold;
        return new PredictionResult(draft, sim, accepted);
    }

    /**
     * Compute acceptance rate over a sequence.
     */
    public static double acceptanceRate(List<CognitiveGenesisProfile> history,
                                         int k,
                                         double threshold) {
        if (history == null || history.size() < k + 1) return 0.0;
        int accepted = 0;
        int total = 0;
        for (int i = k; i < history.size(); i++) {
            List<CognitiveGenesisProfile> window = history.subList(0, i);
            CognitiveGenesisProfile actual = history.get(i);
            PredictionResult r = speculate(window, actual, k, threshold);
            if (r.accepted()) accepted++;
            total++;
        }
        return total == 0 ? 0.0 : (double) accepted / total;
    }
}
