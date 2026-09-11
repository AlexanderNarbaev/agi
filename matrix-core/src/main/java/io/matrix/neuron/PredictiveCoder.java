package io.matrix.neuron;

/**
 * DESIGN-43 — Predictive Coding (Rao-Ballard 1999).
 * Pure function (CONSTITUTION I).
 */
public final class PredictiveCoder {

    public record PredictionError(double magnitude, double[] corrected) {}

    private PredictiveCoder() {}

    public static PredictionError computeError(double[] observation,
                                                double[] prediction) {
        if (observation == null || prediction == null) {
            throw new IllegalArgumentException("null");
        }
        if (observation.length != prediction.length) {
            throw new IllegalArgumentException("dim mismatch");
        }
        double[] error = new double[observation.length];
        double sumSq = 0;
        for (int i = 0; i < observation.length; i++) {
            error[i] = observation[i] - prediction[i];
            sumSq += error[i] * error[i];
        }
        // Corrected = prediction + error = observation (so corrected = observation)
        // More interesting: corrected = prediction - η * error
        // We return the raw error
        return new PredictionError(Math.sqrt(sumSq), error);
    }

    /** Update prediction: new = old + η * error */
    public static double[] update(double[] prediction, double[] error,
                                 double learningRate) {
        if (learningRate < 0) {
            throw new IllegalArgumentException("η ≥ 0");
        }
        double[] updated = new double[prediction.length];
        for (int i = 0; i < prediction.length; i++) {
            updated[i] = prediction[i] + learningRate * error[i];
        }
        return updated;
    }
}
