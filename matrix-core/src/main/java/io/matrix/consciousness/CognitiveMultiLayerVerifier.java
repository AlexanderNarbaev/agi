package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W233 — Cognitive Multi-Layer Verifier.
 *
 * <p>Inspired by LLM multi-layer verification (OpenAI 2025). Generate
 * answer with multiple "models" (verifier strategies) and compare for
 * consistency.
 *
 * <p>Verifier strategies:
 * - V1: similarity to KB
 * - V2: similarity to history
 * - V3: speculative prediction
 * - V4: RAG retrieval
 *
 * <p>Agreement across all = high confidence.
 *
 * <p>CONSTITUTION VI compliance: multi-strategy cognitive verification,
 * not phenomenal consciousness claim.
 */
public final class CognitiveMultiLayerVerifier {

    private CognitiveMultiLayerVerifier() {}

    /** Single verifier result. */
    public record VerifierVote(String strategy, boolean supported, double score) {}

    /** Multi-layer verification result. */
    public record MultiLayerResult(
        List<VerifierVote> votes,
        int supportedCount,
        int totalVotes,
        double consensusScore,
        boolean agreed
    ) {}

    /**
     * Run all verifiers on a profile and aggregate votes.
     */
    public static MultiLayerResult verify(CognitiveGenesisProfile profile,
                                            List<CognitiveGenesisProfile> knowledgeBase,
                                            List<CognitiveGenesisProfile> history,
                                            double threshold) {
        if (profile == null) {
            return new MultiLayerResult(new ArrayList<>(), 0, 0, 0.0, false);
        }
        CognitiveEmbedding emb = new CognitiveEmbedding(64, 0xC09F1107L);
        double[] pVec = emb.embed(profile);
        List<VerifierVote> votes = new ArrayList<>();

        // V1: KB similarity
        if (knowledgeBase != null && !knowledgeBase.isEmpty()) {
            double maxSim = Double.NEGATIVE_INFINITY;
            for (CognitiveGenesisProfile kb : knowledgeBase) {
                double[] v = emb.embed(kb);
                double s = CognitiveEmbedding.cosineSimilarity(pVec, v);
                if (s > maxSim) maxSim = s;
            }
            votes.add(new VerifierVote("KB", maxSim >= threshold, maxSim));
        }
        // V2: history similarity
        if (history != null && !history.isEmpty()) {
            double maxSim = Double.NEGATIVE_INFINITY;
            for (CognitiveGenesisProfile h : history) {
                double[] v = emb.embed(h);
                double s = CognitiveEmbedding.cosineSimilarity(pVec, v);
                if (s > maxSim) maxSim = s;
            }
            votes.add(new VerifierVote("HISTORY", maxSim >= threshold, maxSim));
        }
        // V3: speculative prediction (last K history)
        if (history != null && history.size() >= 3) {
            CognitiveGenesisProfile draft = ProfileSpeculativePredictor.draftPredict(
                history.subList(history.size() - 2, history.size()), 1);
            if (draft != null) {
                double[] dVec = emb.embed(draft);
                double sim = CognitiveEmbedding.cosineSimilarity(pVec, dVec);
                votes.add(new VerifierVote("SPECULATIVE", sim >= threshold, sim));
            }
        }
        // V4: RAG (simplified: nearest KB neighbor)
        if (knowledgeBase != null && !knowledgeBase.isEmpty()) {
            int nearestIdx = 0;
            double bestSim = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < knowledgeBase.size(); i++) {
                double[] v = emb.embed(knowledgeBase.get(i));
                double s = CognitiveEmbedding.cosineSimilarity(pVec, v);
                if (s > bestSim) { bestSim = s; nearestIdx = i; }
            }
            votes.add(new VerifierVote("RAG", bestSim >= threshold, bestSim));
        }
        int supported = 0;
        double totalScore = 0;
        for (VerifierVote v : votes) {
            if (v.supported()) supported++;
            totalScore += v.score();
        }
        int total = votes.size();
        double consensus = total == 0 ? 0.0 : totalScore / total;
        boolean agreed = total > 0 && supported == total;
        return new MultiLayerResult(votes, supported, total, consensus, agreed);
    }
}
