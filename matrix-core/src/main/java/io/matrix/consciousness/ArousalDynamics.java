package io.matrix.consciousness;

/**
 * RUN 157 — ArousalDynamics (vigilance/bдительность).
 *
 * <p>Tracks the cognitive arousal level ∈ [0, 1]. Arousal rises
 * with prediction-error (we're uncertain), novelty, and ethical
 * triggers. It decays toward baseline when the cycle is stable.
 *
 * <p>Per CONSTITUTION I: arousal is fully deterministic — no
 * randomness, no wall-clock branching. Updates are pure
 * functions of input state.
 */
public final class ArousalDynamics {

    public static final double BASELINE = 0.3;

    private double arousal = BASELINE;
    private final double decayRate;
    private final double maxRise;

    public ArousalDynamics() {
        this(0.05, 0.4);
    }

    public ArousalDynamics(double decayRate, double maxRise) {
        this.decayRate = decayRate;
        this.maxRise = maxRise;
    }

    /**
     * Update arousal given a prediction-error ∈ [0, 1+].
     * Higher error → bigger rise. Returns the new arousal.
     */
    public double update(double predictionError) {
        double rise = Math.min(maxRise, predictionError * maxRise);
        arousal = arousal + rise - (arousal - BASELINE) * decayRate;
        arousal = clamp(arousal);
        return arousal;
    }

    /** Decay toward baseline without external input. */
    public double idle() {
        arousal = arousal - (arousal - BASELINE) * decayRate;
        arousal = clamp(arousal);
        return arousal;
    }

    public double current() { return arousal; }

    public void reset() { arousal = BASELINE; }

    private static double clamp(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
