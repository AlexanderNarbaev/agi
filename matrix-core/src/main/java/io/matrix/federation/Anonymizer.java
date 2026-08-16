package io.matrix.federation;

import java.util.HashMap;
import java.util.Map;

/**
 * K-anonymity checker for federation telemetry (DESIGN-08 §5).
 *
 * <p>Ensures that any published telemetry data is shared by at least k
 * distinct nodes before it's considered anonymous. k=100 per DESIGN-08.
 *
 * <p>Prevents single-node attribution of knowledge contributions.
 */
public final class Anonymizer {

    private final int kThreshold;
    private final Map<String, Integer> nodeCounts = new HashMap<>();

    public Anonymizer(int kThreshold) {
        if (kThreshold < 2) throw new IllegalArgumentException("k >= 2");
        this.kThreshold = kThreshold;
    }

    /** Record a contribution from a node. */
    public void recordContribution(String contentHash, String nodeId) {
        nodeCounts.merge(contentHash, 1, Integer::sum);
    }

    /** Check if a content hash is k-anonymous. */
    public boolean isAnonymous(String contentHash) {
        return nodeCounts.getOrDefault(contentHash, 0) >= kThreshold;
    }

    /** Get anonymity level for a content hash. */
    public int anonymityLevel(String contentHash) {
        return nodeCounts.getOrDefault(contentHash, 0);
    }

    /** Get k threshold. */
    public int kThreshold() { return kThreshold; }

    /** Clear all records (for testing). */
    public void clear() { nodeCounts.clear(); }
}
