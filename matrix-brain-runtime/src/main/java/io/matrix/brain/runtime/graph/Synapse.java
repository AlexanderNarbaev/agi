package io.matrix.brain.runtime.graph;

import java.util.Objects;

/**
 * A directed, weighted edge between two {@link CognitiveNode}s.
 *
 * <p>Weight is constrained to {@code [-1, 1]} by {@link #clamp}, matching the brief. The sign
 * carries inhibition: a negative weight attenuates and inverts the signal, so a node can suppress
 * a downstream effect without needing a separate inhibitory edge type.</p>
 *
 * <p>{@link SynapseState} records the synapse's disposition independently of its weight, so a
 * pruned edge keeps its last weight for audit instead of being erased.</p>
 *
 * @param from  source node
 * @param to    target node
 * @param weight edge weight in [-1, 1]
 * @param state lifecycle disposition
 */
public record Synapse(NodeId from, NodeId to, double weight, SynapseState state) {

    /** Bound on |weight|; the brief specifies the range [-1.0, 1.0]. */
    public static final double WEIGHT_BOUND = 1.0;

    public Synapse {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(state, "state");
        weight = clamp(weight);
    }

    /** Clamps a raw weight into [-1, 1], mapping NaN to 0 rather than propagating it. */
    public static double clamp(double w) {
        if (Double.isNaN(w)) return 0.0;
        if (w > WEIGHT_BOUND) return WEIGHT_BOUND;
        if (w < -WEIGHT_BOUND) return -WEIGHT_BOUND;
        return w;
    }

    /** True if this edge still carries signal. */
    public boolean conducting() {
        return state != SynapseState.PRUNED && weight != 0.0;
    }

    /** Scales a signal by this edge's weight. */
    public double apply(double signal) {
        return conducting() ? signal * weight : 0.0;
    }

    /** Returns a copy with a new weight, clamped and state updated for potentiation/depression. */
    public Synapse withWeight(double newWeight) {
        double clamped = clamp(newWeight);
        SynapseState next = state;
        if (state != SynapseState.PRUNED) {
            if (Math.abs(clamped) > Math.abs(weight)) {
                next = SynapseState.POTENTIATED;
            } else if (Math.abs(clamped) < Math.abs(weight)) {
                next = SynapseState.DRESSED_DOWN;
            }
        }
        return new Synapse(from, to, clamped, next);
    }

    /** Returns a copy marked pruned. */
    public Synapse pruned() {
        return new Synapse(from, to, weight, SynapseState.PRUNED);
    }

    /** Returns a copy reconnected after pruning. */
    public Synapse reconnected() {
        return new Synapse(from, to, weight, SynapseState.CONNECTED);
    }

    /** Lifecycle state of a synapse. */
    public enum SynapseState {
        /** Carrying signal at its nominal weight. */
        CONNECTED,
        /** Recently strengthened. */
        POTENTIATED,
        /** Recently weakened. */
        DRESSED_DOWN,
        /** Removed from propagation; retained for audit. */
        PRUNED
    }
}
