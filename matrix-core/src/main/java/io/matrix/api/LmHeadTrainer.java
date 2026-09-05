package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 10 — LM head trainer.
 *
 * <p>Trains the {@link LmHead} from Q&A corpus pairs. For each pair:
 * <ol>
 *   <li>Encode the question via BPE → boolean[] input bits</li>
 *   <li>Run the chain on the input → boolean[] chain_output</li>
 *   <li>Tokenize the answer → answer_token_ids</li>
 *   <li>Update the LM head with (chain_output, answer_token) pairs</li>
 * </ol>
 *
 * <p>The chain_output captures the question's "meaning" in the boolean
 * domain, and the LM head learns which tokens tend to follow when those
 * neuron activations appear.
 *
 * <p>After training, generation uses the LM head to score candidate tokens
 * instead of the hash-based neuron table lookup. This produces
 * corpus-aligned output instead of random neuron firings.
 */
@ApplicationScoped
public class LmHeadTrainer {

    private static final Logger log = LoggerFactory.getLogger(LmHeadTrainer.class);

    @Inject
    BooleanChainRunner chainRunner;

    @Inject
    BpeTokenizerProvider bpeProvider;

    @Inject
    QaCorpusIndex qaIndex;

    @Inject
    ChainFeatureCache featureCache;

    /** The trained LM head. CDI-managed. */
    private final LmHead lmHead = new LmHead();

    private final AtomicBoolean trained = new AtomicBoolean(false);
    private final AtomicLong trainedPairs = new AtomicLong();
    private final AtomicLong trainedEpochs = new AtomicLong();
    private volatile String lastTrainedAt = "never";

    /** On-disk location for the trained weights. */
    public static final String WEIGHTS_PATH = "data/lm_head_weights.bin";

    void onStart(@Observes StartupEvent ev) {
        // Initialize the LM head's neuron count from the chain
        int n = (int) chainRunner.totalNeurons();
        lmHead.setTotalNeurons(n);
        // RUN 15: expose the LM head to ChainFeatureCache so it can
        // produce features with the right dimension.
        ChainFeatureCache.LmHeadTrainerHolder.lmHead(lmHead);
        log.info("LmHeadTrainer: ready (neurons={})", n);

        // Try to load saved weights
        java.io.File f = new java.io.File(WEIGHTS_PATH);
        if (f.exists()) {
            try {
                lmHead.load(WEIGHTS_PATH);
                trained.set(true);
                lastTrainedAt = "loaded from " + WEIGHTS_PATH;
                log.info("LmHeadTrainer: loaded saved weights (vocab={}, updates={})",
                        lmHead.vocabularyCoverage(), lmHead.updateCount());
            } catch (Exception e) {
                log.warn("LmHeadTrainer: failed to load weights: {}", e.getMessage());
            }
        }
    }

    /**
     * Train on the Q&A corpus with default nNegatives=0 (no negative sampling).
     *
     * <p>RUN 11: This 2-arg overload defaults to nNegatives=0 to preserve
     * the original (RUN 10) behavior. The 3-arg overload {@link #train(int, int, int)}
     * takes an explicit nNegatives for users who want contrastive learning.
     *
     * <p>The HTTP endpoint {@code POST /v1/lm-head/train} accepts an
     * {@code nNegatives} query parameter (default 0) and exposes the
     * actual count in the response.
     */
    public synchronized TrainResult train(int limit, int epochs) {
        return train(limit, epochs, 0);  // 0 negatives by default for API stability
    }

    /**
     * Train with explicit negative-sample count.
     * @param nNegatives number of negative tokens sampled per positive update.
     *                   Negative sampling prevents mode collapse on common tokens.
     */
    public synchronized TrainResult train(int limit, int epochs, int nNegatives) {
        if (qaIndex == null || qaIndex.size() == 0) {
            return new TrainResult(0, 0, 0, "qa corpus is empty");
        }
        var entries = qaIndex.state().entries;
        int n = Math.min(limit, entries.size());
        log.info("LmHeadTrainer: training on {} pairs × {} epochs ({} negatives each)",
                n, epochs, nNegatives);

        long totalUpdates = 0;
        for (int e = 0; e < epochs; e++) {
            for (int i = 0; i < n; i++) {
                var entry = entries.get(i);
                int updates = trainOne(entry.question(), entry.answer(), nNegatives);
                totalUpdates += updates;
            }
            trainedEpochs.incrementAndGet();
        }
        trainedPairs.addAndGet(n);
        trained.set(true);
        lastTrainedAt = java.time.Instant.now().toString();

        // Save weights to disk for persistence
        try {
            lmHead.save(WEIGHTS_PATH);
            log.info("LmHeadTrainer: saved weights to {}", WEIGHTS_PATH);
        } catch (Exception ex) {
            log.warn("LmHeadTrainer: failed to save weights: {}", ex.getMessage());
        }

        log.info("LmHeadTrainer: training complete ({} updates, vocab={})",
                totalUpdates, lmHead.vocabularyCoverage());
        return new TrainResult(n * epochs, totalUpdates, lmHead.vocabularyCoverage(), null);
    }

    /** Train on a single (question, answer) pair. */
    public int trainOne(String question, String answer) {
        return trainOne(question, answer, 0);
    }

    /**
     * Train on a single (question, answer) pair with negative sampling.
     *
     * <p>RUN 15: features are the chain's actual output for the question,
     * cached in {@link ChainFeatureCache}. RUN 11 previously used a hash
     * pseudo-fingerprint as a fast proxy; that proxy was prompt-aware
     * but not corpus-aligned. RUN 15 swaps in the real chain output,
     * which makes the LM head learn "given what the chain actually
     * produced for this question, which tokens follow" instead of
     * "given a random fingerprint".
     *
     * @param nNegatives number of negative samples (RUN 11 — prevents
     *                   the LM head from collapsing on common tokens
     *                   like `:` by also decrementing random other
     *                   tokens' weights for the same fingerprint)
     * @return number of token updates applied
     */
    public int trainOne(String question, String answer, int nNegatives) {
        if (question == null || answer == null || question.isEmpty()) return 0;

        // 1. Use the chain's real output as features (RUN 15).
        //    ChainFeatureCache provides question -> boolean[totalN] mapping.
        int totalN = lmHead.totalNeurons();
        if (totalN <= 0) {
            totalN = (int) chainRunner.totalNeurons();
            if (totalN <= 0) return 0;
            lmHead.setTotalNeurons(totalN);
        }

        boolean[] chainOutput;
        if (featureCache != null) {
            chainOutput = featureCache.getOrCompute(question);
        } else {
            // Fallback for tests where CDI didn't wire the cache.
            chainOutput = new boolean[totalN];  // zero vector (no signal)
        }
        if (chainOutput == null) chainOutput = new boolean[totalN];
        // Ensure correct length (cache returns a sized vector, but be defensive).
        if (chainOutput.length != totalN) {
            boolean[] sized = new boolean[totalN];
            System.arraycopy(chainOutput, 0, sized, 0, Math.min(chainOutput.length, totalN));
            chainOutput = sized;
        }

        // 2. Tokenize answer → tokens
        int[] answerTokens;
        if (bpeProvider != null && bpeProvider.isAvailable()) {
            answerTokens = bpeProvider.encode(answer);
        } else {
            answerTokens = new int[Math.min(answer.length(), 64)];
            for (int i = 0; i < answerTokens.length; i++) {
                answerTokens[i] = answer.charAt(i) & 0xFF;
            }
        }
        if (answerTokens == null) answerTokens = new int[0];
        if (answerTokens.length > 64) {
            int[] truncated = new int[64];
            System.arraycopy(answerTokens, 0, truncated, 0, 64);
            answerTokens = truncated;
        }
        if (answerTokens.length == 0) return 0;

        // 3. Update LM head for each answer token (with negative sampling)
        for (int token : answerTokens) {
            if (token >= 0 && token < 200000) {
                lmHead.update(chainOutput, token, nNegatives);
            }
        }
        return answerTokens.length;
    }

    /** Get the trained LM head (used by ChainTextGenerator). */
    public LmHead lmHead() {
        return lmHead;
    }

    public boolean isTrained() { return trained.get(); }
    public long trainedPairs() { return trainedPairs.get(); }
    public long trainedEpochs() { return trainedEpochs.get(); }
    public String lastTrainedAt() { return lastTrainedAt; }

    /** Result of a training run. */
    public record TrainResult(int pairs, long updates, int vocabSize, String error) {
        public Map<String, Object> toMap() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pairs", pairs);
            body.put("updates", updates);
            body.put("vocab_size", vocabSize);
            if (error != null) body.put("error", error);
            return body;
        }
    }
}
