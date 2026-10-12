package io.matrix.brain.runtime.graph;

/**
 * Identity of a node in a {@link Connectome}.
 *
 * <p>A {@code NodeId} is a validated, non-blank name. Ordering is lexicographic and
 * <b>total</b>: two distinct ids always compare unequal in a defined direction. That property is
 * load-bearing — {@link Connectome#propagate} breaks score ties by node id rather than by hash
 * iteration order, which would otherwise make activation non-deterministic across JVM runs.</p>
 *
 * @param value the node's name; must be non-blank
 * @throws IllegalArgumentException if {@code value} is null or blank
 */
public record NodeId(String value) implements Comparable<NodeId> {

    public NodeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("node id must be non-blank");
        }
        value = value.trim();
    }

    /** Convenience factory, equivalent to {@code new NodeId(name)}. */
    public static NodeId of(String name) {
        return new NodeId(name);
    }

    @Override
    public int compareTo(NodeId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}