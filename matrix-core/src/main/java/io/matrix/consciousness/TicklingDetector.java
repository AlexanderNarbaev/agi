package io.matrix.consciousness;

/**
 * RUN 483 — TicklingDetector (DESIGN-63).
 *
 * <p>Detects "tickling" — the case where both halves of a system receive
 * the same copied signal and raw Φ would falsely show high integration.
 * Uses ΦR vs Φ_binary comparison: when ΦR << Φ_binary, system is
 * likely being "tickled" (receiving duplicated input).
 *
 * <h2>Algorithm</h2>
 * <pre>
 *   ticklingScore = 1 - (ΦR / Φ_binary)
 *   if ticklingScore > threshold AND Φ_binary > 0:
 *     ticklingFlag = true
 * </pre>
 *
 * <p>A score near 1 means ΦR is much smaller than Φ_binary, indicating
 * that most of the apparent integration is actually redundant
 * transmission rather than genuine integration.
 *
 * <h2>CONSTITUTION I</h2>
 * Pure function. No side effects.
 */
public final class TicklingDetector {

    private TicklingDetector() {}

    /** Default threshold above which tickling flag is raised. */
    public static final double DEFAULT_THRESHOLD = 0.5;

    /**
     * Compare ΦR and Φ_binary. Returns a tickling score in [0, 1].
     * 0 = no tickling, 1 = pure tickling.
     */
    public static double ticklingScore(double phiBinary, double phiR) {
        if (phiBinary <= 0) {
            // No apparent integration → no tickling concern
            return 0.0;
        }
        if (phiR <= 0) {
            // Tickling: ΦR is zero but Φ_binary is positive
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - (phiR / phiBinary)));
    }

    /**
     * Determine if a system is in a tickling state.
     *
     * @param phiBinary raw integrated information (Tononi 2004)
     * @param phiR      redundancy-suppressing integrated information (Mediano 2022)
     * @param threshold tickling score above which to raise the flag (default 0.5)
     * @return TicklingResult with score and flag
     */
    public static TicklingResult detect(double phiBinary, double phiR, double threshold) {
        double score = ticklingScore(phiBinary, phiR);
        boolean flag = (score > threshold) && (phiBinary > 0);
        return new TicklingResult(score, flag, phiBinary, phiR);
    }

    public static TicklingResult detect(double phiBinary, double phiR) {
        return detect(phiBinary, phiR, DEFAULT_THRESHOLD);
    }

    /**
     * Result of tickling detection.
     */
    public record TicklingResult(
            double ticklingScore,
            boolean ticklingFlag,
            double phiBinary,
            double phiR) {
        /** True if this state indicates genuine integration (not tickling). */
        public boolean isGenuineIntegration() {
            return !ticklingFlag && phiBinary > 0;
        }
    }
}
