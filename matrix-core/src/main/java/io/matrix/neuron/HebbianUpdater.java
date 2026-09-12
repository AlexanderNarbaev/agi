package io.matrix.neuron;

/**
 * RUN 441 — Hebbian learning with decay (DESIGN-54 §4.1).
 *
 * <p>Local Hebbian update rule operating on bipolar ({@code +1, -1})
 * vectors. Maintains a {@link State} that combines a bipolar weight
 * vector (compatible with {@link BitLinear#forward}) and an internal
 * real-valued accumulator (per-bit magnitude) used during updates.
 *
 * <h2>Update rule</h2>
 * <pre>
 *   if pre[i] == post[i]:  acc[i] += eta    (Hebbian reinforcement)
 *   else:                  acc[i] -= eta    (anti-Hebbian weakening)
 *   acc[i] *= (1 - lambda)                  (decay toward zero)
 *   weight[i] = +1 if acc[i] &gt; 0, else -1 (sign of accumulator)
 * </pre>
 *
 * <p>Decay keeps the accumulator bounded; sign-of-accumulator yields a
 * bipolar weight. There's no explicit zero in this encoding — weights
 * are always either +1 or -1, which is fine for {@link BitLinear#forward}
 * since the absmean quantizer handles zero weights identically to
 * negative contributions.
 *
 * <h2>CONSTITUTION I</h2>
 * No RNG, no wall-clock. Caller provides pre/post/eta/lambda and gets a
 * deterministic new state.
 */
public final class HebbianUpdater {

    /** Threshold for accumulator sign quantization: |acc| &gt; this → use ±1, else 0. */
    public static final float DEFAULT_THRESHOLD = 0.1f;

    private HebbianUpdater() {}

    /**
     * Mutable Hebbian state: bipolar weights (long[WORDS]) plus the
     * real-valued accumulator (float[DIM]) that drives the next update.
     */
    public static final class State {
        /** Bipolar weight vector: bit=1 → +1, bit=0 → -1. */
        public final long[] weights;
        /** Per-position accumulator (real-valued magnitude). */
        public final float[] accumulator;

        public State(long[] weights, float[] accumulator) {
            this.weights = weights;
            this.accumulator = accumulator;
        }
    }

    /**
     * Create an initial state with all-zero weights (mapped to bipolar -1)
     * and all-zero accumulators.
     */
    public static State empty() {
        long[] w = new long[HdcEncoding.WORDS];
        // all bits = 0 means all -1 in bipolar; that's our "zero weight"
        // for the purposes of this implementation.
        return new State(w, new float[HdcEncoding.DIM]);
    }

    /**
     * Run one Hebbian update step. Mutates and returns the input state.
     *
     * @param state     current state (mutated in place)
     * @param pre       pre-synaptic bipolar vector (long[WORDS])
     * @param post      post-synaptic bipolar vector (long[WORDS])
     * @param eta       learning rate in (0, 1]
     * @param lambda    decay rate in [0, 1]
     * @return the same {@code state} (mutated)
     */
    public static State update(State state, long[] pre, long[] post,
                                float eta, float lambda) {
        return update(state, pre, post, eta, lambda, DEFAULT_THRESHOLD);
    }

    /**
     * Run one Hebbian update with explicit threshold.
     */
    public static State update(State state, long[] pre, long[] post,
                                float eta, float lambda, float threshold) {
        if (state == null) throw new IllegalArgumentException("null state");
        if (state.weights == null || state.weights.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("state.weights wrong length");
        }
        if (state.accumulator == null || state.accumulator.length != HdcEncoding.DIM) {
            throw new IllegalArgumentException("state.accumulator wrong length");
        }
        if (pre == null || pre.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("pre wrong length");
        }
        if (post == null || post.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("post wrong length");
        }
        if (eta <= 0.0f || eta > 1.0f) {
            throw new IllegalArgumentException("eta must be in (0, 1]");
        }
        if (lambda < 0.0f || lambda > 1.0f) {
            throw new IllegalArgumentException("lambda must be in [0, 1]");
        }
        if (threshold < 0.0f) {
            throw new IllegalArgumentException("threshold must be ≥ 0");
        }

        long[] weights = state.weights;
        float[] acc = state.accumulator;

        for (int w = 0; w < HdcEncoding.WORDS; w++) {
            long wp = pre[w];
            long wq = post[w];
            long disagree = wp ^ wq;
            long outWord = 0L;
            for (int b = 0; b < 64; b++) {
                int pos = (w << 6) + b;
                boolean agree = ((disagree >>> b) & 1L) == 0L;
                float delta = agree ? 1.0f : -1.0f;
                // Update accumulator: add eta*delta, decay toward zero
                float updated = (acc[pos] + eta * delta) * (1.0f - lambda);
                acc[pos] = updated;
                // Threshold to bipolar sign
                if (updated > threshold) {
                    outWord |= (1L << b);  // +1
                } else if (updated < -threshold) {
                    // -1: leave bit clear (bipolar encoding: 0 = -1)
                } else {
                    // Within deadband — keep prior sign via current weight bit
                    outWord |= ((weights[w] >>> b) & 1L) << b;
                }
            }
            weights[w] = outWord;
        }

        return state;
    }

    /**
     * Get the current bipolar weight vector (read-only).
     */
    public static long[] weights(State state) {
        return state.weights;
    }

    /**
     * Count positions where accumulator is non-zero (above threshold).
     * Useful for monitoring learning progress.
     */
    public static int activeCount(State state) {
        return activeCount(state, DEFAULT_THRESHOLD);
    }

    public static int activeCount(State state, float threshold) {
        if (state == null || state.accumulator == null) return 0;
        int n = 0;
        for (float v : state.accumulator) {
            if (Math.abs(v) > threshold) n++;
        }
        return n;
    }

    /**
     * Count positions where the current weight is +1 (bit=1 in bipolar).
     */
    public static int positiveCount(State state) {
        if (state == null || state.weights == null) return 0;
        int n = 0;
        for (long word : state.weights) {
            n += Long.bitCount(word);
        }
        return n;
    }
}
