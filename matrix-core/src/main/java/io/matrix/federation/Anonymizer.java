package io.matrix.federation;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * K-anonymity checker for federation telemetry (DESIGN-08 §5).
 *
 * <p>Ensures that any published telemetry data is shared by at least k
 * distinct nodes before it's considered anonymous. k=100 per DESIGN-08.
 *
 * <p>Prevents single-node attribution of knowledge contributions.
 *
 * <p><b>k counts DISTINCT NODES, not contributions.</b> A node that records the
 * same content hash a thousand times is still ONE node, and a digest it alone
 * holds is not k-anonymous however many times it replays it. The counter
 * therefore holds the SET of contributing node ids per content hash, and the
 * anonymity level is the size of that set. Counting contributions instead would
 * let a single node reach the threshold on its own and publish a digest that is
 * attributable to exactly one node — the failure the whole class exists to
 * prevent.
 */
public final class Anonymizer {

    private final int kThreshold;
    /** The distinct contributing nodes per content hash. */
    private final Map<String, Set<String>> contributors = new ConcurrentHashMap<>();

    public Anonymizer(int kThreshold) {
        if (kThreshold < 2) throw new IllegalArgumentException("k >= 2");
        this.kThreshold = kThreshold;
    }

    /**
     * Record a contribution from a node.
     *
     * <p>Recording the same content hash again from the same node does NOT raise
     * the anonymity level: the node is already counted.
     *
     * @param contentHash the hash of the contributed content
     * @param nodeId the contributing node; a contribution must come from a node
     */
    public void recordContribution(String contentHash, String nodeId) {
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(nodeId, "nodeId");
        contributors.computeIfAbsent(contentHash, key -> ConcurrentHashMap.newKeySet())
                .add(nodeId);
    }

    /** Check if a content hash is k-anonymous, i.e. held by k distinct nodes. */
    public boolean isAnonymous(String contentHash) {
        return nodeCounts(contentHash) >= kThreshold;
    }

    /** Get the number of DISTINCT nodes holding a content hash. */
    public int anonymityLevel(String contentHash) {
        return nodeCounts(contentHash);
    }

    /** Get k threshold. */
    public int kThreshold() { return kThreshold; }

    /** Snapshot of all known content hashes with their distinct-node counts. */
    public java.util.Map<String, Integer> snapshotEntries() {
        Map<String, Integer> snapshot = new java.util.HashMap<>();
        contributors.forEach((hash, nodes) -> snapshot.put(hash, nodes.size()));
        return snapshot;
    }

    /** Clear all records (for testing). */
    public void clear() { contributors.clear(); }

    /** The distinct contributing nodes of one content hash. */
    private int nodeCounts(String contentHash) {
        Set<String> nodes = contributors.get(contentHash);
        return nodes == null ? 0 : nodes.size();
    }
}
