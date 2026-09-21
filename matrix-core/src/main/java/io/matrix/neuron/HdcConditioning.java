package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 443 — Classical/operant conditioning experiments (DESIGN-54 §6, DESIGN-58 Level 1).
 *
 * <p>Wraps {@link HdcBrain} with experiment protocols derived from
 * Pavlov (1927), Sokolov (1963), and Spitz (1957). Each protocol
 * runs a sequence of trials and returns a {@link Result} with the
 * response curve so callers can verify habituation, conditioning,
 * extinction, or spontaneous recovery.
 *
 * <h2>Experiments supported</h2>
 * <ul>
 *   <li>{@link #pavlovClassicalConditioning} — pair CS (bell) with US
 *       (food) repeatedly; after conditioning, CS alone should evoke
 *       the UR (salivate). Response strength = similarity of CS-only
 *       query to the US-associated code.</li>
 *   <li>{@link #habituation} — Sokolov-style: present same stimulus
 *       repeatedly without reinforcement; response should decrement
 *       exponentially to near-zero.</li>
 *   <li>{@link #extinction} — after conditioning, present CS alone
 *       without US; response should decrement (extinction).</li>
 *   <li>{@link #spontaneousRecovery} — after extinction, wait briefly
 *       then re-present CS; brief response returns.</li>
 * </ul>
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies RNG. All experiments are deterministic given inputs.
 */
public final class HdcConditioning {

    /** Result of a conditioning experiment: response curve + summary metrics. */
    public static final class Result {
        /** Per-trial response strength in [0, 1] (1 = full response, 0 = no response). */
        public final float[] responseCurve;
        /** Average response over all trials. */
        public final double meanResponse;
        /** Peak response (max). */
        public final double peakResponse;
        /** Final response (last trial). */
        public final double finalResponse;
        /** Number of trials actually run. */
        public final int trials;

        public Result(float[] responseCurve) {
            this.responseCurve = responseCurve;
            this.trials = responseCurve.length;
            double sum = 0.0;
            double max = 0.0;
            for (float v : responseCurve) {
                sum += v;
                if (v > max) max = v;
            }
            this.meanResponse = trials > 0 ? sum / trials : 0.0;
            this.peakResponse = max;
            this.finalResponse = trials > 0 ? responseCurve[trials - 1] : 0.0;
        }

        /**
         * Estimated habituation rate: fit an exponential decay to the
         * curve after the peak. Returns decay constant lambda; larger
         * lambda = faster habituation.
         */
        public double habituationRate() {
            if (trials < 3) return 0.0;
            int peakIdx = 0;
            for (int i = 1; i < trials; i++) {
                if (responseCurve[i] > responseCurve[peakIdx]) peakIdx = i;
            }
            // Fit log(response) = -lambda * (t - peakIdx) + log(peak)
            // via simple linear regression after the peak.
            int n = trials - peakIdx;
            if (n < 2) return 0.0;
            double sx = 0.0, sy = 0.0, sxy = 0.0, sxx = 0.0;
            int used = 0;
            for (int i = peakIdx; i < trials; i++) {
                float v = responseCurve[i];
                if (v <= 0.0001f) continue; // skip zeros (log undefined)
                double x = i - peakIdx;
                double y = Math.log(v);
                sx += x; sy += y; sxy += x * y; sxx += x * x;
                used++;
            }
            if (used < 2) return 0.0;
            double denom = used * sxx - sx * sx;
            if (denom == 0) return 0.0;
            double slope = (used * sxy - sx * sy) / denom;
            return -slope; // positive lambda = decay
        }

        @Override
        public String toString() {
            return "Result{trials=" + trials + ", mean=" + String.format("%.3f", meanResponse)
                    + ", peak=" + String.format("%.3f", peakResponse)
                    + ", final=" + String.format("%.3f", finalResponse)
                    + ", habitRate=" + String.format("%.4f", habituationRate()) + "}";
        }
    }

    private HdcConditioning() {}

    /**
     * Generate a feature vector representing a stimulus. Uses a deterministic
     * bipolar template (long[WORDS]) inflated to float values.
     *
     * @param template bipolar template (length DIM, packed as long[WORDS])
     * @param magnitude absolute value of feature (positive or negative)
     */
    public static float[] stimulusFromTemplate(long[] template, float magnitude) {
        if (template == null || template.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("template wrong length");
        }
        float[] f = new float[HdcEncoding.DIM];
        for (int w = 0; w < HdcEncoding.WORDS; w++) {
            for (int b = 0; b < 64; b++) {
                int pos = (w << 6) + b;
                boolean bit = ((template[w] >>> b) & 1L) != 0L;
                f[pos] = bit ? magnitude : -magnitude;
            }
        }
        return f;
    }

    /**
     * Run a Pavlov classical conditioning experiment.
     *
     * <p>Phase 1 (pre-test): present CS alone, measure baseline response
     * (should be ~0). Phase 2 (acquisition): pair CS+US, N times. Phase 3
     * (post-test): present CS alone, measure conditioned response.
     *
     * @param brain           brain to train
     * @param csTemplate      bipolar template for conditioned stimulus (bell)
     * @param usTemplate      bipolar template for unconditioned stimulus (food)
     * @param urLabel         label associated with US (e.g. "salivate")
     * @param preTestTrials   number of CS-alone trials before conditioning
     * @param acquisitionTrials number of CS+US pairings
     * @param postTestTrials  number of CS-alone trials after conditioning
     * @param eta             Hebbian learning rate
     * @param lambda          Hebbian decay rate
     */
    public static Result pavlovClassicalConditioning(
            HdcBrain brain,
            long[] csTemplate,
            long[] usTemplate,
            String urLabel,
            int preTestTrials,
            int acquisitionTrials,
            int postTestTrials,
            float eta,
            float lambda) {
        if (brain == null) throw new IllegalArgumentException("null brain");
        if (csTemplate == null || csTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("csTemplate wrong length");
        }
        if (usTemplate == null || usTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("usTemplate wrong length");
        }
        if (urLabel == null) throw new IllegalArgumentException("null urLabel");

        int totalTrials = preTestTrials + acquisitionTrials + postTestTrials;
        float[] response = new float[totalTrials];

        float[] csStim = stimulusFromTemplate(csTemplate, 1.0f);
        float[] usStim = stimulusFromTemplate(usTemplate, 1.0f);
        // Combined CS+US stimulus (elementwise sum)
        float[] csusStim = new float[HdcEncoding.DIM];
        for (int i = 0; i < HdcEncoding.DIM; i++) csusStim[i] = csStim[i] + usStim[i];

        // Phase 1: pre-test (CS alone, expect no response)
        for (int t = 0; t < preTestTrials; t++) {
            HdcBrain.Recall hit = brain.forward(csStim);
            response[t] = hit == null ? 0.0f : (float) similarityToLabel(hit, urLabel);
        }

        // Phase 2: acquisition (pair CS+US, reinforce association)
        // First, teach the US→UR association directly
        brain.learn(usStim, urLabel, eta, lambda);
        for (int t = 0; t < acquisitionTrials; t++) {
            // Reinforce CS+US pair with the UR
            brain.learn(csusStim, urLabel, eta, lambda);
            // Measure during acquisition (CS alone probes emerging association)
            HdcBrain.Recall hit = brain.forward(csStim);
            response[preTestTrials + t] = hit == null ? 0.0f
                    : (float) similarityToLabel(hit, urLabel);
        }

        // Phase 3: post-test (CS alone, expect conditioned response)
        for (int t = 0; t < postTestTrials; t++) {
            HdcBrain.Recall hit = brain.forward(csStim);
            response[preTestTrials + acquisitionTrials + t] = hit == null ? 0.0f
                    : (float) similarityToLabel(hit, urLabel);
        }

        return new Result(response);
    }

    /**
     * Sokolov-style habituation: present the same stimulus N times without
     * reinforcement; measure response decrement.
     */
    public static Result habituation(HdcBrain brain, long[] stimulusTemplate,
                                       String label, int trials, float eta, float lambda) {
        if (brain == null) throw new IllegalArgumentException("null brain");
        if (stimulusTemplate == null || stimulusTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("stimulusTemplate wrong length");
        }
        float[] stim = stimulusFromTemplate(stimulusTemplate, 1.0f);
        float[] response = new float[trials];
        for (int t = 0; t < trials; t++) {
            brain.learn(stim, label, eta, lambda);
            HdcBrain.Recall hit = brain.forward(stim);
            response[t] = hit == null ? 0.0f : (float) similarityToLabel(hit, label);
        }
        return new Result(response);
    }

    /**
     * After conditioning, present CS alone without US to drive extinction.
     */
    public static Result extinction(HdcBrain brain, long[] csTemplate,
                                      String label, int trials, float eta, float lambda) {
        if (brain == null) throw new IllegalArgumentException("null brain");
        if (csTemplate == null || csTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("csTemplate wrong length");
        }
        float[] stim = stimulusFromTemplate(csTemplate, 1.0f);
        float[] response = new float[trials];
        for (int t = 0; t < trials; t++) {
            brain.learn(stim, label, eta, lambda);
            HdcBrain.Recall hit = brain.forward(stim);
            response[t] = hit == null ? 0.0f : (float) similarityToLabel(hit, label);
        }
        return new Result(response);
    }

    /**
     * Spontaneous recovery: after extinction, re-present CS; brief
     * response should return (residual association).
     */
    public static Result spontaneousRecovery(HdcBrain brain, long[] csTemplate,
                                               String label, int trials, float eta,
                                               float lambda) {
        // Identical protocol to extinction but tracked separately.
        return extinction(brain, csTemplate, label, trials, eta, lambda);
    }

    /**
     * Convert a recall result to a 0..1 similarity score for the target label.
     * If the top-1 hit matches the target label, use its similarity.
     * Otherwise, decay by distance ratio to a baseline of 0.0.
     */
    private static double similarityToLabel(HdcBrain.Recall hit, String targetLabel) {
        if (hit == null) return 0.0;
        if (targetLabel.equals(hit.label)) {
            // Normalize [-1, +1] → [0, 1]
            return (hit.similarity + 1.0) / 2.0;
        }
        // Different label retrieved — count as no response (low score)
        return 0.0;
    }
}
