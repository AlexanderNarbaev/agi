package io.matrix.consensus;

/**
 * Computes voting weight based on Proof-of-Accuracy.
 *
 * <p>{@code weight = f(accuracy, uptime, contribution)}.
 * Output is clamped to [0, 1].
 *
 * <p>Ref: L2_Iteraction_protocol.md §6.2
 */
public final class WeightCalculator {

    private static final double ACCURACY_WEIGHT = 0.5;
    private static final double UPTIME_WEIGHT = 0.2;
    private static final double CONTRIBUTION_WEIGHT = 0.3;

    /**
     * Computes voting weight for a node.
     *
     * @param accuracy      sliding average prediction accuracy [0..1]
     * @param uptime        normalized uptime ratio [0..1]
     * @param contributions number of successful mutations contributed
     * @param maxContributions maximum contributions across all nodes
     */
    public static double compute(double accuracy, double uptime,
                                  long contributions, long maxContributions) {
        double accuracyComponent = ACCURACY_WEIGHT * accuracy;
        double uptimeComponent = UPTIME_WEIGHT * uptime;

        double contributionRatio = maxContributions > 0
                ? (double) contributions / maxContributions
                : 0.0;
        double contributionComponent = CONTRIBUTION_WEIGHT * contributionRatio;

        return clamp(accuracyComponent + uptimeComponent + contributionComponent);
    }

    /**
     * Default weight for a new node with no history.
     */
    public static double defaultWeight() {
        return 0.1;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
