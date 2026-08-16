package io.matrix.noosphere;

/**
 * Distributed quorum checker for Noosphere mesh consensus.
 *
 * <p>Determines whether a sufficient number of replicas have acknowledged
 * a proposed state change. Default threshold is 3/5 (60%), a common choice
 * for Byzantine fault-tolerant systems where up to f=1 faulty node is
 * tolerated in a group of n=3f+1=4, rounded up to 5 for safety.
 *
 * <p>All methods are static and thread-safe.
 *
 * <p>Ref: DESIGN-08 §Federation, Phase 5 Noosphere ROADMAP,
 * Lamport et al. "The Byzantine Generals Problem" (1982)
 */
public final class QuorumChecker {

    /** Default quorum ratio: 3/5 = 60%. */
    public static final double DEFAULT_QUORUM_RATIO = 3.0 / 5.0;

    private QuorumChecker() {
        // utility class
    }

    /**
     * Checks whether the number of received responses meets the default
     * 3/5 quorum threshold.
     *
     * @param received number of positive responses received
     * @param total    total number of replicas in the group
     * @return true if quorum is reached
     * @throws IllegalArgumentException if received &gt; total or total &lt;= 0
     */
    public static boolean hasQuorum(int received, int total) {
        int threshold = (int) Math.ceil(total * DEFAULT_QUORUM_RATIO);
        return hasQuorum(received, total, threshold);
    }

    /**
     * Checks whether the number of received responses meets an explicit
     * threshold requirement.
     *
     * @param received  number of positive responses received
     * @param total     total number of replicas in the group
     * @param threshold minimum number of responses needed for quorum
     * @return true if quorum is reached
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public static boolean hasQuorum(int received, int total, int threshold) {
        if (total <= 0) {
            throw new IllegalArgumentException("total must be positive, got: " + total);
        }
        if (received < 0 || received > total) {
            throw new IllegalArgumentException(
                    "received must be in [0, " + total + "], got: " + received);
        }
        if (threshold < 0 || threshold > total) {
            throw new IllegalArgumentException(
                    "threshold must be in [0, " + total + "], got: " + threshold);
        }
        return received >= threshold;
    }
}
