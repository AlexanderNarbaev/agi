package io.matrix.consciousness;

/**
 * W331 — Cognitive YaRN (Yet another RoPE extensioN).
 *
 * <p>Inspired by YaRN (Peng et al. 2023). Extends effective context
 * length of RoPE-based attention by 16-32x without retraining.
 *
 * <p>Key insight: combine frequency-dependent scaling with attention
 * temperature adjustment.
 *
 * <p>CONSTITUTION VI compliance: extended-position cognitive substrate,
 * not phenomenal consciousness claim.
 */
public final class CognitiveYaRN {

    private CognitiveYaRN() {}

    private static final double DEFAULT_BASE = 10000.0;
    private static final double SCALE_FACTOR = 16.0; // 16x context extension

    /**
     * Apply YaRN frequency scaling.
     *
     * @param dim vector dimension
     * @param position original position
     * @param base theta base
     * @param scale factor to extend context (1.0 = no extension)
     * @return adjusted theta for the position
     */
    public static double yarnFrequency(int dim, int position, double base, double scale) {
        if (scale <= 0) scale = 1.0;
        // YaRN: theta_eff = position / (base^(2i/d) * scale)
        // For simplicity, we return the inverse frequency for each dim
        return 1.0 / Math.pow(base, (double)(2 * (dim / 2)) / dim * scale);
    }

    /**
     * Apply YaRN attention temperature.
     * T_eff = T / sqrt(1 + scale^2) for attention scaling
     *
     * @param baseTemp base temperature (1.0 = standard)
     * @param scale context extension factor
     * @return adjusted temperature
     */
    public static double yarnTemperature(double baseTemp, double scale) {
        if (scale <= 0) scale = 1.0;
        return baseTemp / Math.sqrt(1.0 + scale * scale);
    }

    /**
     * Apply YaRN positional encoding (combined).
     */
    public static double[] apply(double[] vector, int position, double scale) {
        if (vector == null) return null;
        double[] result = vector.clone();
        int dim = vector.length;
        // YaRN: scale rotation frequencies based on position
        double effectiveScale = Math.max(1.0, scale);
        for (int i = 0; i < dim; i += 2) {
            double theta = position / Math.pow(DEFAULT_BASE, (double) i / dim * effectiveScale);
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            double x0 = vector[i];
            double x1 = vector[i + 1];
            result[i] = x0 * cos - x1 * sin;
            result[i + 1] = x0 * sin + x1 * cos;
        }
        return result;
    }

    /**
     * Compute effective context length multiplier.
     */
    public static double contextMultiplier(double scale) {
        return Math.max(1.0, scale);
    }
}
