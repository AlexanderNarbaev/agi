package io.matrix.federation;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RUN 48 — Share-impulse firer (H-049 verification).
 *
 * <p>H-049 hypothesis: share-impulse fires when M3 quorum
 * acceptance crosses utility threshold θ_s.
 *
 * <p>This is a STANDALONE synthetic implementation that:
 * <ol>
 *   <li>Tracks a stream of (utility, accepted) pairs from M3 quorum.</li>
 *   <li>Fires a "share impulse" when utility > θ_s AND accepted.</li>
 *   <li>Counts fired impulses and computes precision/recall.</li>
 * </ol>
 *
 * <p>Honest caveat: synthetic. Production would integrate with the
 * actual MeshFederation / M3 quorum / FnlGate pipeline.
 */
public class ShareImpulseFirer {

    /** Default utility threshold. */
    public static final double DEFAULT_THRESHOLD = 0.5;

    private final double threshold;
    private final AtomicLong firedCount = new AtomicLong();
    private final AtomicLong totalAccepted = new AtomicLong();
    private final AtomicLong totalRejected = new AtomicLong();
    private final AtomicLong falseNegatives = new AtomicLong();
    private final AtomicLong truePositives = new AtomicLong();
    private final AtomicLong falsePositives = new AtomicLong();

    public ShareImpulseFirer() {
        this(DEFAULT_THRESHOLD);
    }

    public ShareImpulseFirer(double threshold) {
        this.threshold = threshold;
    }

    /**
     * Observe a (utility, accepted) pair from M3 quorum and decide
     * whether to fire a share impulse.
     *
     * @param utility  M3 utility metric (0..1)
     * @param accepted whether M3 accepted the impulse
     * @param groundTruth whether the impulse SHOULD have fired (for
     *                   precision/recall measurement)
     * @return true if a share impulse was fired
     */
    public boolean observe(double utility, boolean accepted, boolean groundTruth) {
        if (accepted) totalAccepted.incrementAndGet();
        else totalRejected.incrementAndGet();

        boolean shouldFire = utility > threshold && accepted;
        if (shouldFire) {
            firedCount.incrementAndGet();
            if (groundTruth) truePositives.incrementAndGet();
            else falsePositives.incrementAndGet();
            return true;
        } else {
            if (groundTruth) falseNegatives.incrementAndGet();
            return false;
        }
    }

    /** Get fired impulse count. */
    public long firedCount() { return firedCount.get(); }

    /** Get total M3 accepted events. */
    public long totalAccepted() { return totalAccepted.get(); }

    /** Get total M3 rejected events. */
    public long totalRejected() { return totalRejected.get(); }

    /** Get precision (TP / (TP + FP)). */
    public double precision() {
        long tp = truePositives.get();
        long fp = falsePositives.get();
        long denom = tp + fp;
        return denom == 0 ? 0.0 : (double) tp / denom;
    }

    /** Get recall (TP / (TP + FN)). */
    public double recall() {
        long tp = truePositives.get();
        long fn = falseNegatives.get();
        long denom = tp + fn;
        return denom == 0 ? 0.0 : (double) tp / denom;
    }

    public double threshold() { return threshold; }
}
