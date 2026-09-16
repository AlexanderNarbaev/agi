package io.matrix.consciousness;

import java.util.List;

/**
 * W232 — Cognitive Hallucination Detector.
 *
 * <p>Inspired by LLM hallucination detection (OpenAI 2025). Detect
 * profiles that are "fabricated" — inconsistent with knowledge base
 * or prior history.
 *
 * <p>Strategy:
 * - Compute similarity to nearest knowledge base entries
 * - Check internal consistency (RAG + history)
 * - Flag profiles with low support
 *
 * <p>Use cases:
 * - Identify unreliable cognitive predictions
 * - Filter out fabricated states
 * - Quality assurance
 *
 * <p>CONSTITUTION VI compliance: hallucination detection in
 * cognitive state, not phenomenal consciousness claim.
 */
public final class CognitiveHallucinationDetector {

    private CognitiveHallucinationDetector() {}

    /** Hallucination report. */
    public record HallucinationReport(
        boolean hallucinated,
        double confidence,        // 0.0 = definitely hallucinated, 1.0 = safe
        double kbSimilarity,      // similarity to KB
        double historyConsistency, // similarity to history
        String reason
    ) {}

    /**
     * Detect if profile is "hallucinated" — unsupported by knowledge base.
     *
     * @param profile profile to check
     * @param knowledgeBase list of "ground truth" profiles
     * @param history recent profile history
     * @param threshold similarity threshold below which we flag
     */
    public static HallucinationReport detect(CognitiveGenesisProfile profile,
                                                List<CognitiveGenesisProfile> knowledgeBase,
                                                List<CognitiveGenesisProfile> history,
                                                double threshold) {
        if (profile == null) {
            return new HallucinationReport(true, 0.0, 0.0, 0.0, "NULL_PROFILE");
        }
        CognitiveEmbedding emb = new CognitiveEmbedding(64, 0xC09F1107L);
        double[] pVec = emb.embed(profile);
        // KB similarity
        double kbSim = 0.0;
        if (knowledgeBase != null && !knowledgeBase.isEmpty()) {
            double maxSim = Double.NEGATIVE_INFINITY;
            for (CognitiveGenesisProfile kb : knowledgeBase) {
                double[] v = emb.embed(kb);
                double s = CognitiveEmbedding.cosineSimilarity(pVec, v);
                if (s > maxSim) maxSim = s;
            }
            kbSim = maxSim;
        }
        // History consistency
        double histCons = 0.0;
        if (history != null && !history.isEmpty()) {
            double maxSim = Double.NEGATIVE_INFINITY;
            for (CognitiveGenesisProfile h : history) {
                double[] v = emb.embed(h);
                double s = CognitiveEmbedding.cosineSimilarity(pVec, v);
                if (s > maxSim) maxSim = s;
            }
            histCons = maxSim;
        }
        boolean kbEmpty = knowledgeBase == null || knowledgeBase.isEmpty();
        boolean histEmpty = history == null || history.isEmpty();
        boolean hallucinated = (!kbEmpty && kbSim < threshold)
                                 || (!histEmpty && histCons < threshold)
                                 || (kbEmpty && histEmpty);
        String reason = hallucinated ? "LOW_SUPPORT" : "SUPPORTED";
        double confidence = (kbSim + histCons) / 2.0;
        if (Double.isNaN(confidence)) confidence = 0.0;
        return new HallucinationReport(hallucinated, confidence, kbSim, histCons, reason);
    }

    /**
     * Filter out hallucinated profiles from a list.
     */
    public static List<CognitiveGenesisProfile> filter(List<CognitiveGenesisProfile> profiles,
                                                          List<CognitiveGenesisProfile> knowledgeBase,
                                                          double threshold) {
        java.util.List<CognitiveGenesisProfile> filtered = new java.util.ArrayList<>();
        if (profiles == null) return filtered;
        for (CognitiveGenesisProfile p : profiles) {
            HallucinationReport r = detect(p, knowledgeBase, null, threshold);
            if (!r.hallucinated()) filtered.add(p);
        }
        return filtered;
    }
}
