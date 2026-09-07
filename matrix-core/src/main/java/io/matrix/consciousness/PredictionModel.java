package io.matrix.consciousness;

import java.util.Arrays;

/**
 * RUN 156 — PredictionModel (self-prediction).
 *
 * <p>Predicts the next state from current state and records the
 * prediction-error (how wrong we were). Per PARADIGM §4.2,
 * the prediction-error drives arousal updates.
 *
 * <p>This is a deterministic linear model: next = W * current + b,
 * where W is a learned weight matrix and b is a bias vector.
 * For now we use a fixed W (identity-like) so the model is
 * predictable.
 */
public final class PredictionModel {

    private final double[] weights;
    private final double bias;
    private double lastPrediction;
    private double lastError;

    public PredictionModel(int size, double bias) {
        if (size <= 0) throw new IllegalArgumentException("size <= 0");
        this.weights = new double[size];
        Arrays.fill(this.weights, 1.0); // identity baseline
        this.bias = bias;
    }

    public double predict(double[] state) {
        if (state == null || state.length != weights.length) {
            throw new IllegalArgumentException("state length mismatch");
        }
        double sum = bias;
        for (int i = 0; i < state.length; i++) sum += weights[i] * state[i];
        lastPrediction = sum;
        return sum;
    }

    /** Compare prediction to actual and compute |predicted - actual|. */
    public double updateError(double actual) {
        lastError = Math.abs(lastPrediction - actual);
        return lastError;
    }

    public double lastPrediction() { return lastPrediction; }
    public double lastError() { return lastError; }
    public int size() { return weights.length; }

    /**
     * Adapt weights: tiny adjustment towards reducing error.
     * Deterministic given deterministic state.
     */
    public void adapt(double[] state, double actual, double learningRate) {
        if (state.length != weights.length) return;
        double delta = actual - lastPrediction;
        for (int i = 0; i < weights.length; i++) {
            weights[i] += learningRate * delta * state[i];
        }
    }
}
