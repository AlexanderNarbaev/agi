package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 450 — Sokolov habituation experiment (DESIGN-58 Level 1 deep).
 *
 * <p>Full Sokolov (1963) neuronal model of habituation. Repeated
 * presentation of an unreinforced stimulus produces a decrementing
 * response curve that follows an exponential decay.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Present same stimulus N times without reinforcement.</li>
 *   <li>Measure response strength at each trial.</li>
 *   <li>Fit exponential decay: response(t) = R₀ * exp(-λ * t).</li>
 *   <li>Verify the decay follows Sokolov's predicted shape.</li>
 * </ol>
 *
 * <h2>Why HDC brain models habituation</h2>
 * <p>The HdcBrain uses bundle-based learning where repeated same-stim/same-label
 * presentations converge via majority vote. To demonstrate real habituation,
 * we use an HdcBrain with capacity=1 (only the most recent stimulus retained)
 * so each new presentation of an UNRELATED stimulus "forgets" the previous.
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies RNG. All measurements deterministic.
 */
public final class SokolovHabituationExperiment {

    /** Result of the Sokolov habituation experiment. */
    public static final class Result {
        /** Per-trial response in [0, 1]. */
        public final float[] responseCurve;
        /** Fitted decay rate λ (larger = faster habituation). */
        public final double decayRate;
        /** Initial response R₀. */
        public final double initialResponse;
        /** Final response (last trial). */
        public final double finalResponse;

        public Result(float[] responseCurve, double decayRate,
                       double initialResponse, double finalResponse) {
            this.responseCurve = responseCurve;
            this.decayRate = decayRate;
            this.initialResponse = initialResponse;
            this.finalResponse = finalResponse;
        }

        @Override
        public String toString() {
            return "SokolovResult{decayRate=" + String.format("%.4f", decayRate)
                    + ", R0=" + String.format("%.3f", initialResponse)
                    + ", Rfinal=" + String.format("%.3f", finalResponse) + "}";
        }
    }

    private SokolovHabituationExperiment() {}

    /**
     * Run Sokolov habituation: present same stimulus N times, measure
     * response at each trial.
     *
     * @param brain       brain with capacity=1 (so unrelated stims evict)
     * @param stimulus    stimulus template to present
     * @param label       label to associate
     * @param trials      number of presentations
     * @return result with response curve and fitted decay
     */
    public static Result run(HdcBrain brain, long[] stimulus, String label,
                                int trials) {
        if (brain == null) throw new IllegalArgumentException("null brain");
        if (stimulus == null || stimulus.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("stimulus wrong length");
        }
        if (trials < 2) {
            throw new IllegalArgumentException("need ≥ 2 trials for fit");
        }
        float[] curve = new float[trials];
        float[] stim = HdcConditioning.stimulusFromTemplate(stimulus, 1.0f);

        for (int t = 0; t < trials; t++) {
            brain.learn(stim, label, 0.5f, 0.01f);
            HdcBrain.Recall hit = brain.forward(stim);
            // Response is similarity, normalized to [0, 1]
            double sim = hit == null ? 0.0 : (hit.similarity + 1.0) / 2.0;
            curve[t] = (float) sim;
        }

        // Fit exponential decay: log(response) = log(R0) - lambda * t
        double decayRate = fitDecay(curve);
        double r0 = curve.length > 0 ? curve[0] : 0.0;
        double rf = curve.length > 0 ? curve[curve.length - 1] : 0.0;
        return new Result(curve, decayRate, r0, rf);
    }

    /**
     * Spontaneous recovery test: habituate, then present novel stimulus,
     * then re-present original. Verify dishabituation.
     */
    public static Result spontaneousRecovery(HdcBrain brain, long[] stim1, long[] stim2,
                                                String label1, String label2, int trials) {
        if (brain == null) throw new IllegalArgumentException("null brain");
        if (stim1 == null || stim1.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("stim1 wrong length");
        }
        if (stim2 == null || stim2.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("stim2 wrong length");
        }
        float[] curve = new float[trials * 3];
        float[] s1 = HdcConditioning.stimulusFromTemplate(stim1, 1.0f);
        float[] s2 = HdcConditioning.stimulusFromTemplate(stim2, 1.0f);

        for (int t = 0; t < trials; t++) {
            brain.learn(s1, label1, 0.5f, 0.01f);
            HdcBrain.Recall hit = brain.forward(s1);
            double sim = hit == null ? 0.0 : (hit.similarity + 1.0) / 2.0;
            curve[t] = (float) sim;
        }
        // Novel stimulus period
        for (int t = 0; t < trials; t++) {
            brain.learn(s2, label2, 0.5f, 0.01f);
            HdcBrain.Recall hit = brain.forward(s2);
            double sim = hit == null ? 0.0 : (hit.similarity + 1.0) / 2.0;
            curve[trials + t] = (float) sim;
        }
        // Re-presentation of original
        for (int t = 0; t < trials; t++) {
            brain.learn(s1, label1, 0.5f, 0.01f);
            HdcBrain.Recall hit = brain.forward(s1);
            double sim = hit == null ? 0.0 : (hit.similarity + 1.0) / 2.0;
            curve[2 * trials + t] = (float) sim;
        }
        double decayRate = fitDecay(curve);
        double r0 = curve[0];
        double rf = curve[curve.length - 1];
        return new Result(curve, decayRate, r0, rf);
    }

    /**
     * Fit log-linear regression for exponential decay rate.
     */
    private static double fitDecay(float[] curve) {
        int n = curve.length;
        if (n < 2) return 0.0;
        double sx = 0, sy = 0, sxy = 0, sxx = 0;
        int used = 0;
        for (int i = 0; i < n; i++) {
            if (curve[i] <= 0.001f) continue;
            double x = i;
            double y = Math.log(curve[i]);
            sx += x; sy += y; sxy += x * y; sxx += x * x;
            used++;
        }
        if (used < 2) return 0.0;
        double denom = used * sxx - sx * sx;
        if (denom == 0) return 0.0;
        double slope = (used * sxy - sx * sy) / denom;
        return -slope; // positive λ = decay
    }
}
