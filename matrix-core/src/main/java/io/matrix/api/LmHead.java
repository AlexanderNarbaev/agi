package io.matrix.api;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 10 — Learned LM head projection.
 *
 * <p>Projects chain output (boolean vector of {@code totalNeurons} bits)
 * to a vocabulary distribution via a sparse learned weight matrix.
 *
 * <p>Training: Hebbian-style online learning. For each (chain_output,
 * target_token) pair, increment weights for firing neurons, decay for
 * non-firing. After enough examples, weights converge to a per-token
 * "fingerprint" that predicts which tokens follow given chain activations.
 *
 * <p>Scoring: for a candidate token T and a chain_output C, the score is:
 * <pre>
 *   score(T) = SUM_i [ C[i] ? W[T][i] : -alpha * W[T][i] ]
 * </pre>
 * where alpha is a decay factor (default 0.1).
 *
 * <p>Storage: sparse — only non-zero weights are kept. For each token,
 * store a {@code (neuronIndices[], values[])} pair. This works because
 * most tokens have very few neurons that meaningfully predict them.
 *
 * <p>This is a Naive Bayes classifier over neuron activations, trained
 * online from Q&A pairs.
 */
public class LmHead {

    /** Number of neurons in the chain output (set when wired). */
    private int totalNeurons = 0;

    /** Learning rate for increments (when neuron fires and token is target). */
    private final double increment = 0.1;

    /** Decay rate for non-firing neurons (when token is target). */
    private final double decay = 0.01;

    /** Score mixing factor: how much to weight firing vs non-firing neurons. */
    private final double alpha = 0.1;

    /** Sparse weights: token_id → (neuron_indices[], values[]). */
    private final java.util.concurrent.ConcurrentHashMap<Integer, TokenWeights> weights =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Telemetry. */
    private final AtomicLong updateCount = new AtomicLong();
    private final AtomicLong queryCount = new AtomicLong();
    /** RUN 22 — telemetry for signed updates. */
    private final AtomicLong positiveUpdateCount = new AtomicLong();
    private final AtomicLong negativeUpdateCount = new AtomicLong();

    /** Set the chain's output dimension (must match chain.neurons count). */
    public void setTotalNeurons(int n) {
        this.totalNeurons = n;
    }

    public int totalNeurons() { return totalNeurons; }

    /**
     * Update weights for a (chain_output, target_token) pair.
     * Called during training from Q&A pairs.
     *
     * <p>RUN 11: Added negative sampling. Without it, the LM head
     * converges on the most common token (e.g., `:` in our corpus)
     * because every (fingerprint, token) update increments the
     * target token's weights without ever telling OTHER tokens to
     * be less likely. Negative sampling picks K random tokens and
     * decrements their weights for the same fingerprint — this
     * provides contrast and prevents mode collapse.
     *
     * <p>RUN 22: Delegates to {@link #applyUpdate(boolean[], int, double)}
     * with positive delta so the signed-update path is the single
     * source of truth for weight mutation.
     *
     * <p>Thread-safety: training is single-threaded (see
     * {@link LmHeadTrainer#train}), so per-token {@code synchronized}
     * blocks in {@code TokenWeights} are sufficient. Concurrent
     * callers from other threads must serialize their calls externally.
     */
    public void update(boolean[] chainOutput, int targetToken) {
        update(chainOutput, targetToken, 0);
    }

    public void update(boolean[] chainOutput, int targetToken, int nNegatives) {
        if (chainOutput == null || targetToken < 0) return;
        if (totalNeurons == 0) totalNeurons = chainOutput.length;

        // Positive update (RUN 22): the per-firing-neuron weight gets
        // +increment, the per-non-firing-neuron weight gets -decay.
        // Same physics as before, just routed through the signed path.
        applyPositiveUpdate(chainOutput, targetToken);

        // Negative sampling: pick deterministic-random tokens and decrement
        // their weights for the same fingerprint. This prevents all tokens
        // from looking similar (the mode-collapse problem).
        //
        // Determinism: seed is derived from targetToken only — no wall-clock
        // (AGENTS.md forbids wall-clock in decision paths), so training is
        // reproducible across runs.
        if (nNegatives > 0) {
            java.util.Random rng = new java.util.Random((long) targetToken * 0x9E3779B97F4A7C15L);
            // RUN 11 fix: vocab range = full Qwen vocab (151643) + headroom, not
            // just the first 100k. BPE specials live at the low end so the
            // old bound (100k) over-sampled specials. Use the full vocab range
            // so negatives include regular tokens that need contrast.
            int negMax = 200000;
            for (int n = 0; n < nNegatives; n++) {
                int negToken;
                do {
                    negToken = rng.nextInt(negMax);
                } while (negToken == targetToken);
                // RUN 22: NEGATIVE delta is now a real signed update, not a
                // special-case hand-wave. The physics is symmetric: a
                // firing-neuron weight for an unrelated token should drop
                // by 0.1 * increment.
                applyUpdate(chainOutput, negToken, -increment * 0.1);
            }
        }
        // updateCount is already incremented by applyPositiveUpdate +
        // applyUpdate, so no top-level increment here.
    }

    /**
     * RUN 22 — Signed weight update for a single (chain_output, token) pair.
     *
     * <p>This is the SINGLE source of truth for weight mutation. The two
     * old positive-only paths (training + negative sampling) both route
     * through here, and feedback-driven negative updates also use this
     * path with a negative {@code delta}.
     *
     * <p>Physics:
     * <ul>
     *   <li>If neuron {@code i} FIRES (chainOutput[i] == true):
     *       {@code weights[token][i] += delta}.</li>
     *   <li>If neuron {@code i} DOES NOT fire:
     *       {@code weights[token][i] += delta * decayRatio}, where
     *       {@code decayRatio} is 0.1 by default. So a negative delta
     *       produces a SMALLER decrement for non-firing neurons than
     *       for firing ones — keeps the gradient sparse.</li>
     * </ul>
     *
     * <p>This means a {@code delta = +0.1} update is roughly equivalent
     * to {@code +0.1} for firing neurons and {@code +0.001} (decay) for
     * non-firing — the original RUN 10 behaviour. A {@code delta = -0.1}
     * update is exactly the mirror: {@code -0.1} for firing,
     * {@code -0.001} for non-firing.
     *
     * @param chainOutput chain's firing pattern (length must match {@code totalNeurons})
     * @param token       target token to update
     * @param delta       signed weight change (positive = stronger, negative = weaker)
     * @return {@code true} if the update was applied, {@code false} if arguments were invalid
     */
    public boolean applyUpdate(boolean[] chainOutput, int token, double delta) {
        if (chainOutput == null || token < 0) return false;
        if (totalNeurons == 0) totalNeurons = chainOutput.length;

        TokenWeights tw = weights.computeIfAbsent(token,
                k -> new TokenWeights(totalNeurons));
        synchronized (tw) {
            // RUN 22: signed update for Firing neurons uses full delta.
            // For non-firing neurons, scale by decayRatio (0.1). Symmetric
            // for positive and negative deltas.
            double nonFiringScale = delta > 0 ? decay / increment : decay / increment;
            for (int i = 0; i < chainOutput.length; i++) {
                if (i >= totalNeurons) break;
                if (chainOutput[i]) {
                    tw.values[i] += delta;
                } else {
                    tw.values[i] += delta * nonFiringScale;
                }
            }
        }
        // Telemetry: count positive and negative updates separately.
        if (delta > 0) positiveUpdateCount.incrementAndGet();
        else if (delta < 0) negativeUpdateCount.incrementAndGet();
        updateCount.incrementAndGet();
        return true;
    }

    /**
     * RUN 22 — Positive-only helper for the original {@link #update(boolean[], int, int)}
     * path. Equivalent to {@code applyUpdate(chainOutput, token, +increment)}
     * but matches the RUN 10/11 physics exactly (firing-neuron gets full
     * increment, non-firing gets the configured decay).
     */
    private void applyPositiveUpdate(boolean[] chainOutput, int targetToken) {
        TokenWeights tw = weights.computeIfAbsent(targetToken,
                k -> new TokenWeights(totalNeurons));
        synchronized (tw) {
            for (int i = 0; i < chainOutput.length; i++) {
                if (i >= totalNeurons) break;
                if (chainOutput[i]) {
                    tw.values[i] += increment;
                } else {
                    tw.values[i] -= decay;
                }
            }
        }
        positiveUpdateCount.incrementAndGet();
    }

    /**
     * Score a candidate token given the chain output.
     * Returns a score in approximately [-1, 1] (unnormalized logit).
     *
     * <p>Read-only; synchronized on the per-token TokenWeights so we don't
     * observe a half-written weight array from a concurrent updater.
     */
    public double score(boolean[] chainOutput, int token) {
        if (chainOutput == null || token < 0) return 0.0;
        TokenWeights tw = weights.get(token);
        if (tw == null) return 0.0;

        queryCount.incrementAndGet();
        double sum = 0.0;
        synchronized (tw) {
            // Sum weights for firing neurons, decay for non-firing
            for (int i = 0; i < chainOutput.length; i++) {
                double w = tw.values[i];
                if (chainOutput[i]) {
                    sum += w;
                } else if (w != 0) {
                    sum -= alpha * w;
                }
            }
        }
        // Normalize by total neurons so scores are comparable across lengths
        if (chainOutput.length > 0) {
            sum /= Math.sqrt(chainOutput.length);
        }
        return sum;
    }

    /**
     * Score and rank all candidate tokens. Returns a sorted (by score desc)
     * array of (token, score) pairs.
     */
    public int[][] scoreAll(boolean[] chainOutput, int[] candidateTokens, int topK) {
        if (chainOutput == null || candidateTokens == null) return new int[0][0];
        double[] scores = new double[candidateTokens.length];
        for (int i = 0; i < candidateTokens.length; i++) {
            scores[i] = score(chainOutput, candidateTokens[i]);
        }
        // Get top-K by score
        int[] idx = java.util.stream.IntStream.range(0, scores.length)
                .boxed()
                .sorted((a, b) -> Double.compare(scores[b], scores[a]))
                .limit(topK)
                .mapToInt(Integer::intValue)
                .toArray();
        int[][] result = new int[idx.length][2];
        for (int i = 0; i < idx.length; i++) {
            result[i][0] = candidateTokens[idx[i]];
            result[i][1] = (int) (scores[idx[i]] * 1000); // quantized for int return
        }
        return result;
    }

    // ─── RUN 23 — confidence calibration ───

    /** Calibration temperature (RUN 23 — H-024). Higher T = softer distribution. */
    private volatile double temperature = 1.0;

    /** Set calibration temperature (RUN 23). */
    public void setTemperature(double t) {
        if (t <= 0) return;
        this.temperature = t;
    }

    /** Get current calibration temperature. */
    public double temperature() { return temperature; }

    /**
     * RUN 23 — Score with calibrated confidence.
     *
     * <p>Returns a {@link ScoreWithConfidence} record containing the raw
     * LM head score (unnormalized logit) and a calibrated confidence in
     * [0, 1] for this token BEING the next token, computed by:
     *
     * <pre>
     *   softmax(score / T) over a candidate distribution
     * </pre>
     *
     * <p>The confidence is computed against a candidate set supplied via
     * {@code candidateTokens}. If the caller has a known candidate set
     * (e.g., BPE vocabulary), use {@link #scoreWithConfidence(boolean[], int, int[])}.
     * Otherwise use {@link #scoreWithConfidence(boolean[], int)} which
     * uses the trained vocabulary as the candidate set.
     *
     * <p>Calibration assumption: when the model is well-calibrated, the
     * expected accuracy of "predict the token with the highest confidence"
     * equals that confidence. EXP-MATRIX.22 measures this on a held-out set.
     */
    public ScoreWithConfidence scoreWithConfidence(boolean[] chainOutput, int token) {
        int[] vocab = new int[weights.size()];
        int idx = 0;
        for (Integer t : weights.keySet()) vocab[idx++] = t;
        return scoreWithConfidence(chainOutput, token, vocab);
    }

    public ScoreWithConfidence scoreWithConfidence(boolean[] chainOutput, int token, int[] candidates) {
        if (chainOutput == null || token < 0) return new ScoreWithConfidence(0.0, 0.0);
        double rawScore = score(chainOutput, token);
        if (candidates == null || candidates.length == 0) {
            return new ScoreWithConfidence(rawScore, 0.0);
        }
        // Compute softmax over candidates
        double[] logits = new double[candidates.length];
        double maxLogit = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < candidates.length; i++) {
            logits[i] = score(chainOutput, candidates[i]) / Math.max(temperature, 1e-6);
            if (logits[i] > maxLogit) maxLogit = logits[i];
        }
        double sum = 0.0;
        for (double l : logits) sum += Math.exp(l - maxLogit);
        double conf = 0.0;
        for (int i = 0; i < candidates.length; i++) {
            if (candidates[i] == token) {
                conf = Math.exp(logits[i] - maxLogit) / sum;
                break;
            }
        }
        return new ScoreWithConfidence(rawScore, conf);
    }

    /**
     * RUN 23 — Result record for {@link #scoreWithConfidence}.
     */
    public record ScoreWithConfidence(double score, double confidence) {
        public boolean isConfident() { return confidence >= 0.5; }
    }

    public long updateCount() { return updateCount.get(); }
    public long queryCount() { return queryCount.get(); }
    /** RUN 22 — number of positive (delta > 0) updates. */
    public long positiveUpdateCount() { return positiveUpdateCount.get(); }
    /** RUN 22 — number of negative (delta < 0) updates. */
    public long negativeUpdateCount() { return negativeUpdateCount.get(); }
    public int vocabularyCoverage() { return weights.size(); }

    // ─── RUN 29 — sparse memory footprint diagnostics ───

    /**
     * Diagnostic: total bytes used by in-memory dense weights
     * (token × neurons × 8 bytes per double). Real memory usage
     * is lower because JVM compresses zero-valued doubles.
     */
    public long denseMemoryBytes() {
        long bytes = 0;
        for (TokenWeights tw : weights.values()) {
            bytes += (long) tw.values.length * 8;
        }
        return bytes;
    }

    /**
     * Diagnostic: total bytes that WOULD be needed by sparse storage
     * (only non-zero entries, packed as int+double pairs).
     */
    public long sparseMemoryBytes() {
        long bytes = 0;
        for (TokenWeights tw : weights.values()) {
            int nz = tw.nonZeroCount();
            bytes += (long) nz * (4 + 8);  // int neuronId + double value
            // Plus per-token overhead: count + entry list
            bytes += 4;
        }
        return bytes;
    }

    /**
     * RUN 29 — sparsity ratio: fraction of weights that are zero.
     * Higher sparsity → more memory savings from compact storage.
     */
    public double sparsityRatio() {
        if (weights.isEmpty() || totalNeurons == 0) return 1.0;
        long totalSlots = (long) weights.size() * totalNeurons;
        long nonZeroSlots = 0;
        for (TokenWeights tw : weights.values()) nonZeroSlots += tw.nonZeroCount();
        return 1.0 - ((double) nonZeroSlots / totalSlots);
    }

    /** Total non-zero weights across all tokens. */
    public long nonZeroWeightCount() {
        long total = 0;
        for (TokenWeights tw : weights.values()) total += tw.nonZeroCount();
        return total;
    }

    /** Total weight slots (would-be dense size). */
    public long totalWeightSlots() {
        return (long) weights.size() * totalNeurons;
    }

    /**
     * Save weights to disk as a compact binary format.
     * Format: [int totalNeurons][int nTokens]
     *   For each token: [int tokenId][int nEntries] [int neuronId][double value]...
     */
    public void save(String path) throws java.io.IOException {
        try (java.io.DataOutputStream out =
                new java.io.DataOutputStream(new java.io.BufferedOutputStream(
                        new java.io.FileOutputStream(path)))) {
            out.writeInt(totalNeurons);
            out.writeInt(weights.size());
            for (var entry : weights.entrySet()) {
                int tokenId = entry.getKey();
                TokenWeights tw = entry.getValue();
                out.writeInt(tokenId);
                synchronized (tw) {
                    int nEntries = tw.nonZeroCount();
                    out.writeInt(nEntries);
                    for (int i = 0; i < tw.values.length; i++) {
                        if (tw.values[i] != 0) {
                            out.writeInt(i);
                            out.writeDouble(tw.values[i]);
                        }
                    }
                }
            }
        }
    }

    /** Load weights from disk. */
    public void load(String path) throws java.io.IOException {
        weights.clear();
        try (java.io.DataInputStream in =
                new java.io.DataInputStream(new java.io.BufferedInputStream(
                        new java.io.FileInputStream(path)))) {
            totalNeurons = in.readInt();
            int nTokens = in.readInt();
            for (int t = 0; t < nTokens; t++) {
                int tokenId = in.readInt();
                int nEntries = in.readInt();
                TokenWeights tw = new TokenWeights(totalNeurons);
                for (int e = 0; e < nEntries; e++) {
                    int neuronId = in.readInt();
                    double value = in.readDouble();
                    tw.values[neuronId] = value;
                }
                weights.put(tokenId, tw);
            }
        }
    }

    /** Sparse token weight storage. */
    private static class TokenWeights {
        final double[] values;
        TokenWeights(int size) {
            this.values = new double[size];
        }
        void increment(int idx, double delta) {
            values[idx] += delta;
        }
        double get(int idx) {
            return values[idx];
        }
        int nonZeroCount() {
            int n = 0;
            for (double v : values) if (v != 0) n++;
            return n;
        }
    }
}
