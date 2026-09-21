package io.matrix.consciousness;

/**
 * W116 — Kolmogorov complexity snapshot (per-cycle measurement).
 *
 * <p>Wraps a Kolmogorov complexity estimate with cycle-level metadata:
 * the trajectory slice used for the estimate, the index in the ring buffer
 * at which it was taken, and the cycle number.
 *
 * <p>This is analogous to InterAgentPhiSnapshot — it lives in a ring buffer
 * inside ConsciousBrain and can be queried for historical complexity values.
 *
 * <p>CONSTITUTION VI compliance: a snapshot of algorithmic complexity
 * measurement, not a phenomenal consciousness claim.
 */
public record KolmogorovComplexitySnapshot(
        int cycleNumber,
        int bufferIndex,
        long[] trajectoryUsed,
        double kolmogorovK,
        long snapshotHash) {

    public KolmogorovComplexitySnapshot {
        if (cycleNumber < 0) {
            throw new IllegalArgumentException("cycleNumber must be ≥ 0");
        }
        if (bufferIndex < 0) {
            throw new IllegalArgumentException("bufferIndex must be ≥ 0");
        }
        if (trajectoryUsed == null) {
            throw new IllegalArgumentException("trajectoryUsed must not be null");
        }
    }

    /**
     * Compute the K estimate for the snapshot's trajectory. Convenient
     * re-computation when the estimate needs to be updated (e.g., when
     * CTM estimator is improved).
     */
    public double recomputeK() {
        return KolmogorovComplexity.estimate(trajectoryUsed);
    }

    /**
     * Complexity ratio: K divided by maximum possible K for this trajectory
     * length (8·length for bit-packed longs). Higher = more random.
     */
    public double normalizedK() {
        if (trajectoryUsed.length == 0) return 0.0;
        double max = 8.0 * trajectoryUsed.length;
        return kolmogorovK / max;
    }
}
