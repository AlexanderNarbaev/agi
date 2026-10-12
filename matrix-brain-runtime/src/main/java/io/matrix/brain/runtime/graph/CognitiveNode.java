package io.matrix.brain.runtime.graph;

/**
 * A cognitive unit in a {@link Connectome}.
 *
 * <p>Replaces the linear pipeline's Stage. Where a Stage was invoked once in a fixed order and
 * returned a value, a node may be invoked many times per step, from several incoming synapses, and
 * carries learned weights across steps.</p>
 *
 * <p>Implementations must be safe for repeated invocation within a step: {@link #process} may be
 * called once per incoming synapse selected by Top-K routing. If a node needs to aggregate, it
 * should do so inside {@link #process} over the signal it is given, not by accumulating
 * cross-invocation state — cross-step state belongs in {@link #updateWeights}.</p>
 *
 * <p><b>Not every node should be plastic.</b> A node that computes a deterministic function
 * (arithmetic, for example) cannot learn: there is no error signal to associate with a weight. The
 * W37 design marks only the stateful association nodes — HDC retrieval, BIR inference — as
 * candidates for plasticity.</p>
 */
public interface CognitiveNode {

    /** This node's identity. Unique within a connectome. */
    NodeId id();

    /**
     * Transforms an incoming activation into this node's output.
     *
     * @param input the activation arriving from an upstream synapse; never null
     * @return the outgoing activation; never null
     */
    Activation process(Activation input);

    /**
     * Applies a learning update after a step, using the activation the node emitted and received.
     *
     * <p>Default implementation is a no-op, so a stateless or deterministic node opts out by not
     * overriding it. Nodes that do override should route the arithmetic through
     * {@link PlasticityRule} rather than mutating weights ad hoc, so the rule stays auditable in
     * one place.</p>
     *
     * @param pre  the activation that entered this node
     * @param post the activation this node emitted
     */
    default void updateWeights(Activation pre, Activation post) {
        // stateless by default
    }

    /** The node's current arousal state. */
    NodeState state();

    /** Lifecycle state of a node, mirroring the brief's Active/Inhibited/Dormant. */
    enum NodeState {
        /** Participating in propagation. */
        ACTIVE,
        /** Excluded from propagation while holding its weights; reversible. */
        INHIBITED,
        /** Not participating and not receiving updates; used for parked subgraphs. */
        DORMANT
    }
}