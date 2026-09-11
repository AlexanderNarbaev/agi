package io.matrix.neuron;

/**
 * DESIGN-35 — 1D Kalman filter for neuron magnitude tracking.
 * Pure function (CONSTITUTION I).
 */
public final class KalmanStateEstimator {

    public record Estimate(double value, double variance) {}

    private KalmanStateEstimator() {}

    /** Predict: state moves with random walk (no control input). */
    public static Estimate predict(Estimate prev, double processVariance) {
        if (processVariance < 0) {
            throw new IllegalArgumentException("Q ≥ 0");
        }
        return new Estimate(prev.value(), prev.variance() + processVariance);
    }

    /**
     * Update with observation: combine prediction with measurement.
     * K = variance / (variance + R); value += K * (obs - value).
     */
    public static Estimate update(Estimate predicted, double observation,
                                  double measurementVariance) {
        if (measurementVariance < 0) {
            throw new IllegalArgumentException("R ≥ 0");
        }
        double K = predicted.variance()
                / (predicted.variance() + measurementVariance);
        double newValue = predicted.value() + K * (observation - predicted.value());
        double newVar = (1 - K) * predicted.variance();
        return new Estimate(newValue, newVar);
    }

    /** Combined: predict + update. */
    public static Estimate filterStep(Estimate prev, double observation,
                                      double processVariance,
                                      double measurementVariance) {
        Estimate predicted = predict(prev, processVariance);
        return update(predicted, observation, measurementVariance);
    }
}
