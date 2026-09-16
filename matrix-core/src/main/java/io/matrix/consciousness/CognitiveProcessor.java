package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W220 — Cognitive Processor.
 *
 * <p>Unified pipeline that combines the 7 LLM-inspired subsystems
 * (W203-W219) into a single processing unit. Designed to be hooked
 * into ConsciousBrain.java as a post-processing step.
 *
 * <p>Pipeline:
 * 1. Embed incoming profile (CognitiveEmbedding)
 * 2. Compute self-attention over history (CognitiveAttention)
 * 3. Update KV cache (ProfileKVCache)
 * 4. Update sliding window (CognitiveSlidingWindow)
 * 5. Speculate next profile (ProfileSpeculativePredictor)
 * 6. Optionally retrieve from RAG (CognitiveRAG)
 *
 * <p>CONSTITUTION VI compliance: integrated cognitive processing
 * pipeline, not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveProcessor {

    /** Configuration for the cognitive processor. */
    public record Config(
        int embeddingDim,
        long embeddingSeed,
        int attentionDim,
        long attentionSeed,
        int kvCachePageSize,
        int kvCacheNumPages,
        int windowSinkCount,
        int windowSize,
        int speculateK,
        double speculateThreshold,
        boolean useRAG,
        int ragK,
        double ragMixingRatio
    ) {
        public static Config defaults() {
            return new Config(64, 42L, 16, 42L, 4, 16, 4, 16, 2, 0.5, false, 3, 0.5);
        }
    }

    private final Config config;
    private final CognitiveEmbedding embedder;
    private final ProfileKVCache kvCache;
    private final CognitiveSlidingWindow slidingWindow;
    private final List<CognitiveGenesisProfile> history;

    public CognitiveProcessor() {
        this(Config.defaults());
    }

    public CognitiveProcessor(Config config) {
        this.config = config;
        this.embedder = new CognitiveEmbedding(config.embeddingDim(), config.embeddingSeed());
        this.kvCache = new ProfileKVCache(config.kvCachePageSize(), config.kvCacheNumPages(), config.embeddingSeed());
        this.slidingWindow = new CognitiveSlidingWindow(config.windowSinkCount(), config.windowSize());
        this.history = new ArrayList<>();
    }

    /**
     * Process an incoming cognitive profile through the full pipeline.
     *
     * @return processing result with embedded vector, attention weights,
     *         and speculative prediction
     */
    public ProcessingResult process(CognitiveGenesisProfile profile) {
        if (profile == null) return new ProcessingResult(null, null, null, false, 0.0);
        history.add(profile);
        // 1. Embed
        double[] embedding = embedder.embed(profile);
        // 2. Self-attention
        CognitiveAttention.AttentionResult attention =
            CognitiveAttention.selfAttention(history, config.attentionDim(), config.attentionSeed());
        // 3. KV cache
        kvCache.append(profile);
        // 4. Sliding window
        slidingWindow.add(profile);
        // 5. Speculation
        boolean speculated = false;
        double similarity = 0.0;
        if (history.size() >= config.speculateK() + 1) {
            List<CognitiveGenesisProfile> past = new ArrayList<>(
                history.subList(0, history.size() - 1));
            ProfileSpeculativePredictor.PredictionResult spec =
                ProfileSpeculativePredictor.speculate(past, profile, config.speculateK(), config.speculateThreshold());
            speculated = spec.accepted();
            similarity = spec.similarity();
        }
        return new ProcessingResult(embedding, attention.attentionWeights(), null, speculated, similarity);
    }

    /**
     * Get current KV cache contents.
     */
    public List<CognitiveGenesisProfile> kvCacheContents() {
        return kvCache.all();
    }

    /**
     * Get current sliding window contents.
     */
    public List<CognitiveGenesisProfile> windowContents() {
        return slidingWindow.all();
    }

    /**
     * Number of profiles processed so far.
     */
    public int historySize() {
        return history.size();
    }

    /** Result of processing a single profile. */
    public record ProcessingResult(
        double[] embedding,
        double[][] attentionWeights,
        CognitiveRAG.RetrievalResult ragResult,
        boolean speculationAccepted,
        double speculationSimilarity
    ) {}
}
