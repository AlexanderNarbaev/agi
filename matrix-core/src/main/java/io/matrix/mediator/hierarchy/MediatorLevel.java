package io.matrix.mediator.hierarchy;

/**
 * Mediator hierarchy levels with default weights.
 *
 * <p>Ref: L4_Mediator.md §2.1
 */
public enum MediatorLevel {
    LOBE(0.2),
    CLUSTER(0.5),
    INSTANCE(0.8),
    GLOBAL(1.0);

    private final double defaultWeight;

    MediatorLevel(double defaultWeight) {
        this.defaultWeight = defaultWeight;
    }

    public double defaultWeight() { return defaultWeight; }

    public boolean isAbove(MediatorLevel other) {
        return this.defaultWeight > other.defaultWeight;
    }

    public boolean isBelow(MediatorLevel other) {
        return this.defaultWeight < other.defaultWeight;
    }
}
