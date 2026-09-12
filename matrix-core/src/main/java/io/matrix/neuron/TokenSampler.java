package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 463 — Token sampling for text generation (DESIGN-54 §13).
 *
 * <p>Combines temperature, top-k, and top-p (nucleus) filtering
 * per the BitNet b1.58 generation_config.json:
 * <pre>
 *   { "do_sample": true, "temperature": 0.6, "top_p": 0.9, ... }
 * </pre>
 *
 * <h2>Algorithm</h2>
 * <pre>
 *   1. Apply temperature: logits /= T (T=1 → unchanged, T→0 → greedy)
 *   2. Convert to probabilities via softmax
 *   3. Top-k: keep only k highest-prob tokens (if k > 0)
 *   4. Top-p: keep smallest set with cumulative prob ≥ p
 *   5. Renormalize remaining probabilities to sum to 1
 *   6. Sample from the filtered distribution
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies Random. All math is pure-function on inputs.
 */
public final class TokenSampler {

    /** Default sampling config matching BitNet generation_config.json. */
    public static final class Config {
        /** Temperature (T=1 unchanged, T→0 → greedy, T→∞ → uniform). */
        public final float temperature;
        /** Top-k (0 = disabled). */
        public final int topK;
        /** Top-p / nucleus (0..1, 1.0 = disabled). */
        public final float topP;
        /** Minimum probability to keep (0 = disabled). */
        public final float minP;

        public Config(float temperature, int topK, float topP, float minP) {
            this.temperature = temperature;
            this.topK = topK;
            this.topP = topP;
            this.minP = minP;
        }

        public static Config defaults() {
            // Matches BitNet b1.58-2B-4T generation_config.json
            return new Config(0.6f, 0, 0.9f, 0.0f);
        }

        public static Config greedy() {
            return new Config(1.0f, 1, 1.0f, 0.0f);
        }
    }

    private TokenSampler() {}

    /**
     * Apply sampling to logits and return a sampled token index.
     */
    public static int sample(float[] logits, Config cfg, Random rng) {
        if (logits == null || logits.length == 0) {
            throw new IllegalArgumentException("empty logits");
        }
        if (cfg == null) throw new IllegalArgumentException("null config");
        if (rng == null) throw new IllegalArgumentException("null rng");

        // 1. Apply temperature: divide logits by T
        // For numerical stability, find max and subtract first (log-sum-exp trick)
        float T = cfg.temperature;
        if (T <= 0) {
            throw new IllegalArgumentException("temperature must be positive");
        }
        // Use shifted logits for numerical stability
        float[] scaledLogits = new float[logits.length];
        float maxLogit = logits[0];
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > maxLogit) maxLogit = logits[i];
        }
        for (int i = 0; i < logits.length; i++) {
            scaledLogits[i] = (logits[i] - maxLogit) / T;
        }

        // 2. Softmax to probabilities
        float[] probs = softmax(scaledLogits);

        // 3. Top-k filtering
        if (cfg.topK > 0 && cfg.topK < probs.length) {
            probs = topKFilter(probs, cfg.topK);
        }

        // 4. Top-p filtering
        if (cfg.topP < 1.0f && cfg.topP > 0) {
            probs = topPFilter(probs, cfg.topP);
        }

        // 5. Min-p filtering
        if (cfg.minP > 0) {
            probs = minPFilter(probs, cfg.minP);
        }

        // 6. Renormalize
        double sum = 0;
        for (float p : probs) sum += p;
        if (sum <= 0) {
            // Degenerate: fall back to uniform
            return rng.nextInt(logits.length);
        }

        // 7. Sample from distribution
        double target = rng.nextDouble() * sum;
        double cumsum = 0;
        for (int i = 0; i < probs.length; i++) {
            cumsum += probs[i];
            if (cumsum >= target) return i;
        }
        return probs.length - 1; // numerical edge case
    }

    /**
     * Greedy argmax (temperature-independent).
     */
    public static int greedy(float[] logits) {
        int maxIdx = 0;
        float maxVal = logits[0];
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > maxVal) {
                maxVal = logits[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    /**
     * Softmax (numerically stable with shift).
     */
    private static float[] softmax(float[] logits) {
        float max = logits[0];
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > max) max = logits[i];
        }
        float sum = 0;
        float[] result = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            result[i] = (float) Math.exp(logits[i] - max);
            sum += result[i];
        }
        float inv = 1.0f / sum;
        for (int i = 0; i < result.length; i++) {
            result[i] *= inv;
        }
        return result;
    }

    /**
     * Keep only top-k highest-probability tokens (zero others).
     */
    private static float[] topKFilter(float[] probs, int k) {
        // Find indices of top-k
        Integer[] indices = new Integer[probs.length];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> Float.compare(probs[b], probs[a]));
        float[] filtered = new float[probs.length];
        for (int i = 0; i < k; i++) {
            filtered[indices[i]] = probs[indices[i]];
        }
        return filtered;
    }

    /**
     * Keep smallest set of tokens with cumulative probability ≥ p.
     */
    private static float[] topPFilter(float[] probs, float p) {
        Integer[] indices = new Integer[probs.length];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> Float.compare(probs[b], probs[a]));
        float cumsum = 0;
        int keepCount = 0;
        for (int i = 0; i < indices.length; i++) {
            cumsum += probs[indices[i]];
            keepCount++;
            if (cumsum >= p) break;
        }
        float[] filtered = new float[probs.length];
        for (int i = 0; i < keepCount; i++) {
            filtered[indices[i]] = probs[indices[i]];
        }
        return filtered;
    }

    /**
     * Keep only tokens with probability ≥ min_p * max_prob.
     */
    private static float[] minPFilter(float[] probs, float minP) {
        float maxProb = 0;
        for (float p : probs) if (p > maxProb) maxProb = p;
        float threshold = minP * maxProb;
        float[] filtered = new float[probs.length];
        for (int i = 0; i < probs.length; i++) {
            if (probs[i] >= threshold) filtered[i] = probs[i];
        }
        return filtered;
    }
}
