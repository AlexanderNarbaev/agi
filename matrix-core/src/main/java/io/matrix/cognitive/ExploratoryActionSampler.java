package io.matrix.cognitive;

import java.util.List;
import java.util.Random;

/**
 * W93 — Bounded exploratory sampler (DESIGN-64).
 *
 * <p>Selects one action from a candidate set using a seeded Random. The seed is
 * derived from a deterministic state hash, so the same starting state produces
 * the same action sequence across runs.
 *
 * <p>This is the "exploratory bounded" regime: bounded to a finite candidate set
 * with a deterministic seed. Used by cognitive layers (action selection, hypothesis
 * generation), NOT by numerical substrate (integration metrics, weight inference).
 *
 * <p>CONSTITUTION I compliance: seed derivation is a pure function of state hash,
 * so two systems starting from the same state explore the same alternatives.
 * Random is permitted here because the cognitive layer is allowed to vary (see
 * DESIGN-64 §2 for the proposed Article I revision).
 */
public final class ExploratoryActionSampler {

    private ExploratoryActionSampler() {}

    /**
     * Sample one index from a candidate set using a deterministic seed derived
     * from the given state hash. Same hash → same index.
     *
     * @param stateHash deterministic hash of the brain state
     * @param candidates non-empty list of candidates
     * @param cycleCount cycle index (further differentiates same state across time)
     * @return selected candidate index in [0, candidates.size())
     */
    public static <T> T sample(long stateHash, List<T> candidates, long cycleCount) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must be non-empty");
        }
        long seed = mix(stateHash, cycleCount);
        Random rng = new Random(seed);
        int idx = rng.nextInt(candidates.size());
        return candidates.get(idx);
    }

    /**
     * Sample k distinct candidates from a larger set (without replacement).
     */
    public static <T> List<T> sampleK(long stateHash, List<T> candidates,
                                       long cycleCount, int k) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must be non-empty");
        }
        if (k <= 0 || k > candidates.size()) {
            throw new IllegalArgumentException("k must be in [1, candidates.size()]");
        }
        long seed = mix(stateHash, cycleCount);
        Random rng = new Random(seed);
        java.util.List<T> pool = new java.util.ArrayList<>(candidates);
        java.util.Collections.shuffle(pool, rng);
        return new java.util.ArrayList<>(pool.subList(0, k));
    }

    /**
     * Produce a deterministic probability distribution over candidates using
     * softmax over a derived score. Same state → same distribution.
     */
    public static <T> double[] softmax(long stateHash, List<T> candidates,
                                        long cycleCount, double temperature) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must be non-empty");
        }
        if (temperature <= 0) {
            throw new IllegalArgumentException("temperature must be > 0");
        }
        long seed = mix(stateHash, cycleCount);
        Random rng = new Random(seed);
        int n = candidates.size();
        double[] logits = new double[n];
        double maxLogit = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            logits[i] = rng.nextGaussian() / temperature;
            if (logits[i] > maxLogit) maxLogit = logits[i];
        }
        double sum = 0;
        double[] probs = new double[n];
        for (int i = 0; i < n; i++) {
            probs[i] = Math.exp(logits[i] - maxLogit);
            sum += probs[i];
        }
        for (int i = 0; i < n; i++) probs[i] /= sum;
        return probs;
    }

    /** Mix two long values into one seed using a 64-bit hash. */
    private static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L;
        h ^= b * 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 30) * 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
