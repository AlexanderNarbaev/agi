package io.matrix.identity;

import java.util.Objects;

/**
 * RUN 221 — Identity (frozen node identifier).
 *
 * <p>Frozen per-node identity (node ID). Each MATRIX instance has
 * a unique, deterministic identity used in federation digests.
 *
 * <p>Per CONSTITUTION, identity should be immutable post-init.
 */
public final class Identity {

    private final String nodeId;
    private final long createdAtMillis;

    public Identity(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must be non-blank");
        }
        this.nodeId = nodeId;
        // Use a non-deterministic source so multiple JVMs have different ids;
        // for federation tests, the caller must inject a deterministic id.
        this.createdAtMillis = System.currentTimeMillis();
    }

    public String nodeId() { return nodeId; }

    public long createdAtMillis() { return createdAtMillis; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Identity)) return false;
        Identity other = (Identity) o;
        return nodeId.equals(other.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }
}
