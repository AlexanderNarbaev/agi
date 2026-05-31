package io.matrix.mediator;

import java.util.Random;

/**
 * Homoeostatic driver variable d_i ∈ [0, 1].
 *
 * <p>Updated each tick by: d(t+1) = clamp(d(t) + α*(target − d(t)) + noise).
 *
 * <p>Ref: L4_Mediator.md §3.3
 */
public final class DriverState {

    private final DriverType type;
    private final double target;
    private final double adaptationRate;
    private final double noiseAmplitude;
    private final double thresholdHigh;
    private final double thresholdLow;

    private double level;

    public DriverState(DriverType type, double initialLevel, double target,
                       double adaptationRate, double noiseAmplitude,
                       double thresholdHigh, double thresholdLow) {
        this.type = type;
        this.level = clamp(initialLevel);
        this.target = target;
        this.adaptationRate = adaptationRate;
        this.noiseAmplitude = noiseAmplitude;
        this.thresholdHigh = thresholdHigh;
        this.thresholdLow = thresholdLow;
    }

    public static DriverState withDefaults(DriverType type) {
        return switch (type) {
            case ENERGY -> new DriverState(type, 0.3, 0.2, 0.1, 0.02, 0.7, 0.1);
            case SAFETY -> new DriverState(type, 0.2, 0.05, 0.05, 0.01, 0.7, 0.1);
            case CURIOSITY -> new DriverState(type, 0.5, 0.4, 0.15, 0.03, 0.7, 0.1);
        };
    }

    /**
     * Updates the driver level according to the homoeostatic formula.
     *
     * @param rng random source for noise
     */
    public void update(Random rng) {
        double noise = (rng.nextDouble() * 2.0 - 1.0) * noiseAmplitude;
        double delta = adaptationRate * (target - level) + noise;
        level = clamp(level + delta);
    }

    public DriverType type() { return type; }

    public double level() { return level; }

    public double target() { return target; }

    public double adaptationRate() { return adaptationRate; }

    public double thresholdHigh() { return thresholdHigh; }

    public double thresholdLow() { return thresholdLow; }

    public boolean isHigh() { return level >= thresholdHigh; }

    public boolean isLow() { return level <= thresholdLow; }

    public boolean isActive() { return !isLow(); }

    /**
     * Nudges the driver level by a delta (clamped to [0, 1]).
     */
    public void nudge(double delta) {
        level = clamp(level + delta);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
