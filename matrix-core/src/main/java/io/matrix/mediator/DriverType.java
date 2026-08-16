package io.matrix.mediator;

/**
 * Motivation driver types.
 *
 * <p>Each driver carries a homeostatic {@code target} value and a human-readable
 * description. The {@code target} is the set-point the driver gravitates toward
 * via the homoeostatic update formula (see {@link DriverState}).
 *
 * <p>Ref: L4_Mediator.md §3.2
 */
public enum DriverType {

    ENERGY(0.2, "Need for computational resources and energy"),

    SAFETY(0.05, "Need for safety and stability"),

    CURIOSITY(0.4, "Need for novelty and learning"),

    ENTROPY(0.5, "Exploration and randomness"),

    SOCIAL(0.7, "Social engagement and communication"),

    SELFACTUALIZATION(0.8, "Growth and mastery"),

    ATTENTION(0.6, "Focus and resource allocation"),

    UBUNTU(0.9, "Collective wellbeing and cooperation");

    private final double target;
    private final String description;

    DriverType(double target, String description) {
        this.target = target;
        this.description = description;
    }

    public double target() { return target; }

    public String description() { return description; }
}
