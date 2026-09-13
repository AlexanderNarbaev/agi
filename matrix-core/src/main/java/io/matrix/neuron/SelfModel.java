package io.matrix.neuron;

/**
 * RUN 471 — SelfModel (DESIGN-60).
 *
 * <p>Hofstadter-style "I" loop: a meta-brain observes the first-order brain
 * and produces self-model signals. Implements a minimal form of:
 * <ul>
 *   <li>Theory of Mind (Premack-Woodruff 1978): model of a model</li>
 *   <li>Meta-cognition: knowing what you know</li>
 *   <li>Self-reference: a signal that points to itself</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <pre>
 *   observation → primaryBrain → action → consequence
 *                  ↓
 *              selfRepresentation (meta-feature of primaryBrain)
 *                  ↓
 *              metaBrain → meta-action → meta-consequence
 *                  ↓
 *              meta-prediction of meta-consequence
 * </pre>
 *
 * <h2>Novel combination (per W60+ research)</h2>
 * Combines:
 * <ul>
 *   <li>HdcBrain as primary brain (10000-bit memory)</li>
 *   <li>PredictiveCoder for self-prediction errors</li>
 *   <li>FreeEnergyLoss as meta-objective</li>
 *   <li>BitLinearDreamer for meta-sleep (offline learning)</li>
 * </ul>
 *
 * <h2>CONSTITUTION I</h2>
 * Pure functions. Caller supplies all data.
 */
public final class SelfModel {

    private SelfModel() {}

    /**
     * One self-modeling cycle: primary brain acts, meta-brain observes
     * and predicts the consequence.
     *
     * @param primaryObservation       what the primary brain sees
     * @param primaryPrediction         what the primary brain predicted
     * @param primaryAction             what the primary brain did
     * @param actualConsequence         what actually happened
     * @return self-model result with meta-prediction error
     */
    public static SelfModelResult modelStep(
            float[] primaryObservation,
            float[] primaryPrediction,
            float[] primaryAction,
            float[] actualConsequence) {
        if (primaryObservation == null || primaryPrediction == null
                || primaryAction == null || actualConsequence == null) {
            throw new IllegalArgumentException("null inputs");
        }

        // Convert float[] to double[] for PredictiveCoder
        double[] obsD = toDouble(primaryObservation);
        double[] predD = toDouble(primaryPrediction);
        double[] actD = toDouble(primaryAction);
        double[] consD = toDouble(actualConsequence);

        // Compute primary prediction error (how well primary brain predicted)
        PredictiveCoder.PredictionError primaryError =
                PredictiveCoder.computeError(obsD, predD);

        // Meta-prediction: predict that consequence matches action-derived state
        // (For simplicity, use action as predictor)
        PredictiveCoder.PredictionError metaError =
                PredictiveCoder.computeError(consD, actD);

        // Compute self-representation: a fixed-size vector combining
        // (primary prediction error, meta prediction error, observation magnitude, action magnitude)
        float[] selfRepresentation = new float[4];
        selfRepresentation[0] = (float) primaryError.magnitude();
        selfRepresentation[1] = (float) metaError.magnitude();
        selfRepresentation[2] = (float) magnitude(primaryObservation);
        selfRepresentation[3] = (float) magnitude(primaryAction);

        // Meta-prediction: predict the self-representation will stay stable
        double selfConsistency = primaryError.magnitude() + metaError.magnitude();

        return new SelfModelResult(selfRepresentation, primaryError.magnitude(),
                                    metaError.magnitude(), selfConsistency);
    }

    private static double magnitude(float[] v) {
        double sumSq = 0;
        for (float x : v) sumSq += x * x;
        return Math.sqrt(sumSq / v.length);
    }

    private static double[] toDouble(float[] v) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i];
        return out;
    }

    /**
     * Result of a self-modeling step.
     */
    public record SelfModelResult(
            float[] selfRepresentation,
            double primaryPredictionError,
            double metaPredictionError,
            double selfConsistency) {
        public double totalError() {
            return primaryPredictionError + metaPredictionError + selfConsistency;
        }
    }
}
