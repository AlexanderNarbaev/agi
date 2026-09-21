package io.matrix.cognitive;

import io.matrix.neuron.HdcEncoding;
import io.matrix.neuron.HebbianUpdater;
import io.matrix.neuron.HebbianUpdater.State;

import java.util.Objects;

/**
 * W100 — ErrorDrivenLearner (DESIGN-64 §4): error-driven binding decay.
 *
 * <p>Reads a CognitiveErrorStream and applies DECAY to a HebbianUpdater.State
 * along the error-observation direction. When the brain records an error
 * (PREDICTION_ERROR, INTEGRATION_VIOLATION, etc.), this learner weakens
 * the HDC bindings along the observed input pattern — making it less
 * likely to repeat the same response to similar inputs.
 *
 * <p>The mechanism: for each error, the recorded observation snapshot is
 * converted to an HDC sign-bit vector (bit i = obs[i] &gt; 0). For each bit
 * i where the current weight is active (=1) AND the error observation has
 * bit i = 1, we apply an anti-Hebbian push: decay the accumulator below
 * -threshold so the bit becomes 0. Bits where observation has 0 are
 * left untouched (they represent "what should be" — preserving the
 * desired signal).
 *
 * <p>This is essentially a targeted forgetting rule (cf. Cichon &amp; Gan,
 * "Sleep replay prevents catastrophic forgetting", 2015): errors forget
 * the failure-specific binding while preserving the background.
 *
 * <p>Deterministic: same input error sequence → same output state.
 *
 * <p>CONSTITUTION VI compliance: this is a learning rule, not a phenomenal
 * consciousness claim.
 */
public final class ErrorDrivenLearner {

    private ErrorDrivenLearner() {}

    /**
     * Apply decay to the binding at the observation direction for each
     * error. State is mutated in place.
     *
     * @param state the Hebbian state to update (mutated)
     * @param stream source of errors; iterated in chronological order
     * @param eta decay strength ∈ (0, 1]; small for stability (e.g. 0.1)
     * @return the same state object (mutated)
     */
    public static State applyDecay(State state, CognitiveErrorStream stream, float eta) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(stream, "stream");
        if (eta <= 0.0f || eta > 1.0f) {
            throw new IllegalArgumentException("eta must be in (0, 1]");
        }
        if (stream.isEmpty()) return state;
        for (CognitiveError error : stream) {
            long[] obs = observationToHdc(error.stateSnapshot());
            // Decay accumulator for bits where obs=1 AND weight is currently active.
            // This is targeted forgetting along the failure direction.
            for (int w = 0; w < HdcEncoding.WORDS; w++) {
                long obsWord = obs[w];
                if (obsWord == 0) continue;
                long activeWord = state.weights[w];
                // Weaken only bits that are both active AND in the observation
                long targetBits = obsWord & activeWord;
                if (targetBits == 0) continue;
                for (int b = 0; b < 64; b++) {
                    if ((targetBits >>> b & 1L) == 0L) continue;
                    int pos = (w << 6) + b;
                    // Decay the accumulator by subtracting a multiple of eta.
                    // If acc was positive (which is why bit was active),
                    // subtracting eta pushes it toward 0 and eventually negative.
                    // For active bits, acc is just above threshold (e.g. 0.1).
                    // We want to flip them to 0.
                    state.accumulator[pos] -= 2.0f * eta;
                    // Re-clamp: if it's now negative enough, the bit will
                    // become 0 when thresholded (handled in HebbianUpdater
                    // apply pass). If not, it stays at the (now smaller) positive.
                }
            }
        }
        return state;
    }

    /**
     * Compute the total active binding count across the stream's errors.
     * Useful for monitoring how many bindings the brain is hedging against.
     */
    public static int totalActiveBindings(State state) {
        if (state == null || state.accumulator == null) return 0;
        int n = 0;
        for (float v : state.accumulator) {
            // Count only positive accumulators (Hebbian-strengthened, not anti-Hebbian)
            if (v > HebbianUpdater.DEFAULT_THRESHOLD) n++;
        }
        return n;
    }

    /**
     * Compute the strength of the binding between pre and the current
     * state at the time of the error. Returns the cosine similarity in [-1, 1].
     */
    public static double errorBindingStrength(State state, CognitiveError error) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(error, "error");
        long[] obs = observationToHdc(error.stateSnapshot());
        return HdcEncoding.similarity(obs, HebbianUpdater.weights(state));
    }

    /**
     * Convert a float[] observation into HDC long[] form using sign-bit
     * representation: bit i = (obs[i] > 0) ? 1 : 0, packed into 64-bit words.
     */
    private static long[] observationToHdc(float[] obs) {
        if (obs == null) throw new IllegalArgumentException("null obs");
        long[] hdc = new long[HdcEncoding.WORDS];
        for (int i = 0; i < Math.min(obs.length, HdcEncoding.DIM); i++) {
            if (obs[i] > 0) {
                hdc[i >>> 6] |= 1L << (i & 63);
            }
        }
        return hdc;
    }
}
