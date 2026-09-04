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

    /** Set the chain's output dimension (must match chain.neurons count). */
    public void setTotalNeurons(int n) {
        this.totalNeurons = n;
    }

    public int totalNeurons() { return totalNeurons; }

    /**
     * Update weights for a (chain_output, target_token) pair.
     * Called during training from Q&A pairs.
     */
    public void update(boolean[] chainOutput, int targetToken) {
        if (chainOutput == null || targetToken < 0) return;
        if (totalNeurons == 0) totalNeurons = chainOutput.length;

        TokenWeights tw = weights.computeIfAbsent(targetToken,
                k -> new TokenWeights(totalNeurons));
        synchronized (tw) {
            for (int i = 0; i < chainOutput.length; i++) {
                if (i >= totalNeurons) break;
                if (chainOutput[i]) {
                    tw.increment(i, increment);
                } else {
                    tw.increment(i, -decay);
                }
            }
        }
        updateCount.incrementAndGet();
    }

    /**
     * Score a candidate token given the chain output.
     * Returns a score in approximately [-1, 1] (unnormalized logit).
     */
    public double score(boolean[] chainOutput, int token) {
        if (chainOutput == null || token < 0) return 0.0;
        TokenWeights tw = weights.get(token);
        if (tw == null) return 0.0;

        queryCount.incrementAndGet();
        double sum = 0.0;
        synchronized (tw) {
            // Sum weights for firing neurons
            int nFiring = 0;
            int nNonFiring = 0;
            for (int i = 0; i < chainOutput.length; i++) {
                double w = tw.get(i);
                if (chainOutput[i]) {
                    sum += w;
                    nFiring++;
                } else if (w != 0) {
                    sum -= alpha * w;
                    nNonFiring++;
                }
            }
            // Normalize by total neurons so scores are comparable across lengths
            if (chainOutput.length > 0) {
                sum /= Math.sqrt(chainOutput.length);
            }
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

    public long updateCount() { return updateCount.get(); }
    public long queryCount() { return queryCount.get(); }
    public int vocabularyCoverage() { return weights.size(); }

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
