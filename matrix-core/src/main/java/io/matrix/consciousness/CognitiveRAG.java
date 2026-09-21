package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * W213 — Cognitive RAG (Retrieval-Augmented Generation).
 *
 * <p>Inspired by RAG (Lewis et al. 2020). For a query cognitive profile,
 * retrieve K most relevant "knowledge" profiles from a knowledge base,
 * then augment the query with retrieved context.
 *
 * <p>Process:
 * 1. Embed query
 * 2. Search knowledge base for K nearest neighbors
 * 3. Aggregate retrieved profiles (mean or weighted)
 * 4. Return augmented profile = mix(query, retrieved)
 *
 * <p>Use cases:
 * - Augment cognitive state with prior knowledge
 * - Bootstrap new profiles from similar past profiles
 * - Memory-augmented cognitive processing
 *
 * <p>CONSTITUTION VI compliance: retrieval-augmented cognitive state,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random instances are seeded.
 */
public final class CognitiveRAG {

    private CognitiveRAG() {}

    /** Result of RAG retrieval. */
    public record RetrievalResult(
        CognitiveGenesisProfile augmentedProfile,
        int[] retrievedIndices,
        double avgSimilarity
    ) {}

    /**
     * Retrieve K nearest profiles from knowledge base and augment query.
     *
     * @param query the profile to augment
     * @param knowledgeBase the knowledge base (list of profiles)
     * @param k number of neighbors to retrieve
     * @param mixingRatio 0.0 = pure query, 1.0 = pure retrieved
     * @return augmented profile + retrieval metadata
     */
    public static RetrievalResult retrieveAndAugment(CognitiveGenesisProfile query,
                                                      List<CognitiveGenesisProfile> knowledgeBase,
                                                      int k,
                                                      double mixingRatio) {
        if (query == null || knowledgeBase == null || knowledgeBase.isEmpty() || k < 1) {
            return new RetrievalResult(query, new int[0], 0.0);
        }
        CognitiveEmbedding emb = new CognitiveEmbedding(64, 0xC09F1107L);
        double[] qVec = emb.embed(query);
        int n = knowledgeBase.size();
        double[] sims = new double[n];
        for (int i = 0; i < n; i++) {
            double[] v = emb.embed(knowledgeBase.get(i));
            sims[i] = CognitiveEmbedding.cosineSimilarity(qVec, v);
        }
        // Top K
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> Double.compare(sims[b], sims[a]));
        int resultK = Math.min(k, n);
        int[] topIndices = new int[resultK];
        double avgSim = 0.0;
        for (int i = 0; i < resultK; i++) {
            topIndices[i] = indices[i];
            avgSim += sims[indices[i]];
        }
        avgSim /= resultK;
        // Compute retrieved profile (mean of top K)
        CognitiveGenesisProfile retrieved = meanProfile(knowledgeBase, topIndices);
        // Mix query and retrieved
        CognitiveGenesisProfile mixed = mix(query, retrieved, mixingRatio);
        return new RetrievalResult(mixed, topIndices, avgSim);
    }

    /**
     * Compute mean profile over selected indices.
     */
    private static CognitiveGenesisProfile meanProfile(List<CognitiveGenesisProfile> profiles, int[] indices) {
        if (indices == null || indices.length == 0) return null;
        double phi = 0, phiF = 0, phiR = 0, phiLG = 0, interAgent = 0;
        double stability = 0, crossLevel = 0, kolmogorov = 0;
        double analogical = 0, exclusion = 0, nkK = 0;
        double memristor = 0, lsystem = 0;
        for (int idx : indices) {
            CognitiveGenesisProfile p = profiles.get(idx);
            phi += p.phiBinary();
            phiF += p.phiF();
            phiR += p.phiR();
            phiLG += p.phiLinGauss();
            interAgent += p.interAgentPhi();
            stability += p.stabilityPhi();
            crossLevel += p.crossLevelPhi();
            kolmogorov += p.kolmogorovK();
            analogical += p.analogicalSimilarity();
            exclusion += p.conceptualExclusion();
            nkK += p.nkEdgeOfChaosK();
            memristor += p.memristorConductance();
            lsystem += p.lSystemComplexityRatio();
        }
        int n = indices.length;
        return new CognitiveGenesisProfile(
            phi / n, phiF / n, phiR / n, phiLG / n,
            interAgent / n, stability / n, crossLevel / n,
            kolmogorov / n,
            analogical / n, exclusion / n,
            (int) (nkK / n),
            memristor / n, lsystem / n
        );
    }

    /**
     * Mix two profiles with given ratio.
     * ratio = 0.0 → result = a; ratio = 1.0 → result = b.
     */
    private static CognitiveGenesisProfile mix(CognitiveGenesisProfile a,
                                                 CognitiveGenesisProfile b,
                                                 double ratio) {
        if (a == null) return b;
        if (b == null) return a;
        double r = Math.max(0.0, Math.min(1.0, ratio));
        return new CognitiveGenesisProfile(
            a.phiBinary() * (1 - r) + b.phiBinary() * r,
            a.phiF() * (1 - r) + b.phiF() * r,
            a.phiR() * (1 - r) + b.phiR() * r,
            a.phiLinGauss() * (1 - r) + b.phiLinGauss() * r,
            a.interAgentPhi() * (1 - r) + b.interAgentPhi() * r,
            a.stabilityPhi() * (1 - r) + b.stabilityPhi() * r,
            a.crossLevelPhi() * (1 - r) + b.crossLevelPhi() * r,
            a.kolmogorovK() * (1 - r) + b.kolmogorovK() * r,
            a.analogicalSimilarity() * (1 - r) + b.analogicalSimilarity() * r,
            a.conceptualExclusion() * (1 - r) + b.conceptualExclusion() * r,
            (int) (a.nkEdgeOfChaosK() * (1 - r) + b.nkEdgeOfChaosK() * r),
            a.memristorConductance() * (1 - r) + b.memristorConductance() * r,
            a.lSystemComplexityRatio() * (1 - r) + b.lSystemComplexityRatio() * r
        );
    }

    /**
     * Find the most relevant documents (profiles) for a query.
     * Returns indices sorted by descending similarity.
     */
    public static int[] topK(CognitiveGenesisProfile query,
                               List<CognitiveGenesisProfile> knowledgeBase,
                               int k) {
        if (query == null || knowledgeBase == null || knowledgeBase.isEmpty() || k < 1) {
            return new int[0];
        }
        CognitiveEmbedding emb = new CognitiveEmbedding(64, 0xC09F1107L);
        double[] qVec = emb.embed(query);
        int n = knowledgeBase.size();
        double[] sims = new double[n];
        for (int i = 0; i < n; i++) {
            double[] v = emb.embed(knowledgeBase.get(i));
            sims[i] = CognitiveEmbedding.cosineSimilarity(qVec, v);
        }
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> Double.compare(sims[b], sims[a]));
        int resultK = Math.min(k, n);
        int[] result = new int[resultK];
        for (int i = 0; i < resultK; i++) result[i] = indices[i];
        return result;
    }
}
