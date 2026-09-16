package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W224 — Cognitive Disaggregation (prefill vs decode-style).
 *
 * <p>Inspired by LLM disaggregation (prefill on compute-bound GPUs,
 * decode on memory-bound GPUs). In MATRIX:
 *
 * <p>PREFILL phase (batch): process all profiles at once
 * - Compute-bound: parallel matrix operations
 * - Used for: full profile analysis, batch attention
 *
 * <p>DECODE phase (incremental): process one profile at a time
 * - Memory-bound: sequential access to history
 * - Used for: streaming attention, speculative prediction
 *
 * <p>Use cases:
 * - Two-phase cognitive processing
 * - Batch + online analysis
 * - Resource-constrained deployment
 *
 * <p>CONSTITUTION VI compliance: two-phase cognitive processing, not
 * phenomenal consciousness claim.
 */
public final class CognitiveDisaggregation {

    private CognitiveDisaggregation() {}

    /** Result of disaggregated processing. */
    public record DisaggregationResult(
        PrefillResult prefillResult,
        DecodeResult decodeResult
    ) {}

    /** Prefill phase result: batch processing. */
    public record PrefillResult(
        int profilesProcessed,
        double[][] batchEmbeddings,
        double batchEntropy,
        long elapsedOps
    ) {}

    /** Decode phase result: sequential processing. */
    public record DecodeResult(
        CognitiveGenesisProfile lastProfile,
        double[] lastEmbedding,
        double acceptanceRate,
        long elapsedOps
    ) {}

    /**
     * Two-phase processing: prefill (batch) then decode (sequential).
     */
    public static DisaggregationResult process(List<CognitiveGenesisProfile> profiles,
                                                  int dim, long seed) {
        PrefillResult prefill = prefillPhase(profiles, dim, seed);
        DecodeResult decode = decodePhase(profiles, dim, seed, 2, 0.5);
        return new DisaggregationResult(prefill, decode);
    }

    /**
     * Prefill phase: batch processing of all profiles.
     */
    public static PrefillResult prefillPhase(List<CognitiveGenesisProfile> profiles,
                                              int dim, long seed) {
        if (profiles == null || profiles.isEmpty()) {
            return new PrefillResult(0, new double[0][], 0.0, 0);
        }
        CognitiveEmbedding embedder = new CognitiveEmbedding(dim, seed);
        int n = profiles.size();
        double[][] embeddings = new double[n][dim];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            embeddings[i] = embedder.embed(profiles.get(i));
            sum += profiles.get(i).unifiedComplexityScore();
        }
        double batchEntropy = sum / n;
        long ops = (long) n * dim * 13; // 13 fields × dim
        return new PrefillResult(n, embeddings, batchEntropy, ops);
    }

    /**
     * Decode phase: sequential processing with speculative decoding.
     */
    public static DecodeResult decodePhase(List<CognitiveGenesisProfile> profiles,
                                              int dim, long seed, int k,
                                              double threshold) {
        if (profiles == null || profiles.isEmpty()) {
            return new DecodeResult(null, new double[0], 0.0, 0);
        }
        CognitiveEmbedding embedder = new CognitiveEmbedding(dim, seed);
        CognitiveGenesisProfile last = profiles.get(profiles.size() - 1);
        double[] lastEmb = embedder.embed(last);
        double acceptance = ProfileSpeculativePredictor.acceptanceRate(profiles, k, threshold);
        long ops = profiles.size() * dim * 13;
        return new DecodeResult(last, lastEmb, acceptance, ops);
    }

    /**
     * Compute speedup: ratio of prefill to decode ops.
     */
    public static double speedupRatio(PrefillResult prefill, DecodeResult decode) {
        if (decode.elapsedOps() == 0) return 0.0;
        return (double) prefill.elapsedOps() / decode.elapsedOps();
    }
}
