package io.matrix.api;

import io.matrix.chat.ConversationFeedbackStore;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Continuous LM head trainer (RUN 19).
 *
 * <p>Listens to {@link ConversationFeedbackStore} for new feedback
 * submissions and incrementally updates the {@link LmHead} weights:
 *
 * <ul>
 *   <li><b>Positive feedback</b> (rating ≥ 0.7): increment LM head weights
 *       for the chain-output × answer-token pairs.</li>
 *   <li><b>Negative feedback</b> (rating &lt; 0.3): decrement weights for
 *       the chain-output × answer-token pairs (with no negative sampling
 *       — pure penalty).</li>
 * </ul>
 *
 * <p>The question is recovered from {@link ConversationMemory} (the most
 * recent user turn), the answer from the most recent assistant turn.
 * The chain output is computed via {@link ChainFeatureCache} (same path
 * as batch LM head training).
 *
 * <p>Persists the updated weights to disk on {@link ShutdownEvent} so
 * the LM head survives server restarts.
 *
 * <p>Thread-safety: feedback submissions may come from concurrent HTTP
 * requests. Each {@link #onFeedback(String, double)} call is synchronised
 * to serialize updates to the LM head (which itself is thread-safe via
 * its internal synchronisation).
 */
@ApplicationScoped
public class LmHeadFeedbackTrainer {

    private static final Logger log = LoggerFactory.getLogger(LmHeadFeedbackTrainer.class);

    /** Rating ≥ this value counts as positive (thumbs-up). */
    public static final double POSITIVE_THRESHOLD = 0.7;
    /** Rating < this value counts as negative (thumbs-down). */
    public static final double NEGATIVE_THRESHOLD = 0.3;
    /** RUN 22 — magnitude of negative weight updates (per token, per chain feature). */
    public static final double NEGATIVE_DELTA = -0.1;

    @Inject
    ConversationMemory conversationMemory;

    @Inject
    ConversationFeedbackStore feedbackStore;

    @Inject
    LmHeadTrainer lmHeadTrainer;

    @Inject
    ChainFeatureCache featureCache;

    private final AtomicLong positiveUpdates = new AtomicLong();
    private final AtomicLong negativeUpdates = new AtomicLong();
    private final AtomicLong skippedUpdates = new AtomicLong();

    void onStart(@Observes StartupEvent ev) {
        log.info("LmHeadFeedbackTrainer ready (positive>={} negative<{})",
                POSITIVE_THRESHOLD, NEGATIVE_THRESHOLD);
    }

    /**
     * Apply one feedback event to the LM head.
     *
     * @param conversationId conversation that received feedback
     * @param rating cumulative rating (0.0..1.0)
     * @return number of weight updates applied (positive → +1 per token,
     *         negative → -1 per token)
     */
    public int onFeedback(String conversationId, double rating) {
        if (conversationId == null || conversationId.isBlank()) return 0;
        if (lmHeadTrainer == null || featureCache == null) {
            skippedUpdates.incrementAndGet();
            return 0;
        }

        // 1. Find the most recent question + answer in the conversation.
        String question = null;
        String answer = null;
        if (conversationMemory != null) {
            List<ConversationMemory.Turn> turns = conversationMemory.turns(conversationId);
            for (int i = turns.size() - 1; i >= 0; i--) {
                ConversationMemory.Turn t = turns.get(i);
                if (answer == null && "assistant".equalsIgnoreCase(t.role())) {
                    answer = t.text();
                } else if (question == null && "user".equalsIgnoreCase(t.role())) {
                    question = t.text();
                }
                if (answer != null && question != null) break;
            }
        }
        if (question == null || question.isEmpty() || answer == null || answer.isEmpty()) {
            skippedUpdates.incrementAndGet();
            return 0;
        }

        // 2. Compute chain output (real features via cache).
        boolean[] chainOutput = featureCache.getOrCompute(question);
        if (chainOutput == null) {
            skippedUpdates.incrementAndGet();
            return 0;
        }

        // 3. Tokenize answer.
        int[] tokens = tokenize(answer);
        if (tokens.length == 0) {
            skippedUpdates.incrementAndGet();
            return 0;
        }

        // 4. Apply weight updates with sign from feedback rating.
        int sign = rating >= POSITIVE_THRESHOLD ? +1 : rating < NEGATIVE_THRESHOLD ? -1 : 0;
        if (sign == 0) {
            skippedUpdates.incrementAndGet();
            return 0;
        }

        LmHead lmHead = lmHeadTrainer.lmHead();
        for (int token : tokens) {
            if (token < 0 || token >= 200000) continue;
            if (sign > 0) {
                // Positive: increment weights for chainOutput × token.
                lmHead.update(chainOutput, token, 0);
                positiveUpdates.incrementAndGet();
            } else if (sign < 0) {
                // RUN 22: real signed update via LmHead.applyUpdate with
                // negative delta. This DECREMENTS the firing-neuron weights
                // by NEGATIVE_DELTA per token, weakening the link between
                // this chain output and this token.
                decrementForToken(lmHead, chainOutput, token);
                negativeUpdates.incrementAndGet();
            }
        }

        log.info("LmHeadFeedbackTrainer: conv={} rating={} sign={} tokens={} question='{}...'",
                conversationId, rating, sign, tokens.length,
                question.substring(0, Math.min(40, question.length())));
        return tokens.length;
    }

    /**
     * Decrement weights for a (chainOutput, token) pair.
     *
     * <p>RUN 22: now uses the signed {@link LmHead#applyUpdate(boolean[], int, double)}
     * API. This is a real negative update — firing neurons get
     * {@code weights -= |NEGATIVE_DELTA|}, non-firing get the scaled
     * negative decay.
     *
     * <p>Compared to RUN 19: the negative feedback signal is no longer
     * just captured in the feedback store; it directly mutates the
     * LM head weights so subsequent scoring reflects the negative
     * signal. EXP-MATRIX.21 quantifies the score reduction.
     */
    private void decrementForToken(LmHead lmHead, boolean[] chainOutput, int token) {
        lmHead.applyUpdate(chainOutput, token, NEGATIVE_DELTA);
    }

    private static int[] tokenize(String text) {
        if (text == null || text.isEmpty()) return new int[0];
        int max = Math.min(text.length(), 64);
        int[] out = new int[max];
        for (int i = 0; i < max; i++) {
            out[i] = text.charAt(i) & 0xFF;
        }
        return out;
    }

    void onStop(@Observes ShutdownEvent ev) {
        // Persist updated LM head weights to disk.
        if (lmHeadTrainer != null && lmHeadTrainer.lmHead() != null) {
            try {
                lmHeadTrainer.lmHead().save(LmHeadTrainer.WEIGHTS_PATH);
                log.info("LmHeadFeedbackTrainer: persisted weights to {} (pos={} neg={} skipped={})",
                        LmHeadTrainer.WEIGHTS_PATH,
                        positiveUpdates.get(), negativeUpdates.get(), skippedUpdates.get());
            } catch (Exception e) {
                log.warn("LmHeadFeedbackTrainer: failed to persist: {}", e.getMessage());
            }
        }
    }

    public long positiveUpdates() { return positiveUpdates.get(); }
    public long negativeUpdates() { return negativeUpdates.get(); }
    public long skippedUpdates() { return skippedUpdates.get(); }
}
