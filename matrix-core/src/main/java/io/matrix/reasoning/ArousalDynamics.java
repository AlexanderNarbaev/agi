package io.matrix.reasoning;

/**
 * RUN 41 — Arousal dynamics (H-050 verification).
 *
 * <p>H-050 hypothesis: arousal monotonically increases under a
 * strictly-increasing prediction-error stream.
 *
 * <p>The arousal-update function is:
 * <pre>
 *   arousal(t+1) = saturate(arousal(t) + α × error(t) - β × arousal(t))
 * </pre>
 *
 * <p>where α is the arousal sensitivity to error (default 0.5),
 * β is the decay rate (default 0.1), and saturate clamps to [0, 1].
 *
 * <p>Properties:
 * <ul>
 *   <li>Strictly increasing error → strictly increasing arousal
 *       (until saturation at 1.0).</li>
 *   <li>Decreasing error → arousal decays toward 0.</li>
 *   <li>Constant error → arousal converges to error / (α/β + 1) =
 *       error × 2.</li>
 * </ul>
 *
 * <p>Honest caveat: this is a simplified linear model. Real arousal
 * dynamics would include nonlinear terms (cortisol, attention
 * gating, etc.). For H-050's monotonicity property the linear model
 * is sufficient.
 */
public class ArousalDynamics {

    /** Sensitivity to prediction-error. */
    private final double alpha;

    /** Decay rate (toward 0). */
    private final double beta;

    /** Current arousal level [0, 1]. */
    private double arousal = 0.0;

    public ArousalDynamics() {
        this(0.5, 0.1);
    }

    public ArousalDynamics(double alpha, double beta) {
        if (alpha <= 0) alpha = 0.5;
        if (beta < 0 || beta > 1) beta = 0.1;
        this.alpha = alpha;
        this.beta = beta;
    }

    /**
     * Update arousal given a new prediction-error value (0..1).
     *
     * @param error prediction error magnitude [0, 1]
     * @return new arousal value [0, 1]
     */
    public double update(double error) {
        if (error < 0) error = 0;
        if (error > 1) error = 1;
        arousal = arousal + alpha * error - beta * arousal;
        if (arousal < 0) arousal = 0;
        if (arousal > 1) arousal = 1;
        return arousal;
    }

    /** Get current arousal level. */
    public double getArousal() { return arousal; }

    /** Reset arousal to initial state. */
    public void reset() { arousal = 0.0; }

    /** Get alpha (sensitivity). */
    public double getAlpha() { return alpha; }

    /** Get beta (decay rate). */
    public double getBeta() { return beta; }
}
