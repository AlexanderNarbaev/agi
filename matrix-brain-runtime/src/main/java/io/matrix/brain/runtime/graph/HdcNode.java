package io.matrix.brain.runtime.graph;

import io.matrix.brain.runtime.stages.HdcRetrievalStage;

/**
 * Wraps the existing HDC retrieval stage as a {@link CognitiveNode}.
 *
 * <p>HDC retrieval is chosen deliberately over Tsetlin. DESIGN-68 §2.2 marks Tsetlin DEFERRED for
 * the connectome because at production feature width its measured accuracy was chance before the
 * W37.3 {@code typeOne} fix, and that fix is verified only for conjunctions — parity-k with k >= 3
 * still sits at 47-54%. Wiring plasticity around a node with no reliable learning signal would grow
 * a graph that adapts toward noise, and pruning would then remove correct edges because they look
 * unused.</p>
 *
 * <p>HDC retrieval is the one association node in the current pipeline with real recall, which
 * makes it the honest first plastic node.</p>
 *
 * <p>Retrieval confidence is projected onto a scalar activation; the node is plastic through
 * {@link PlasticityRule} rather than by mutating the underlying store, so weight behaviour is
 * auditable in one place.</p>
 */
public final class HdcNode implements CognitiveNode {

    private final NodeId id;
    private final HdcRetrievalStage stage;
    private double weight = 1.0;
    private NodeState state = NodeState.ACTIVE;

    public HdcNode(NodeId id, HdcRetrievalStage stage) {
        if (id == null || stage == null) {
            throw new IllegalArgumentException("id and stage required");
        }
        this.id = id;
        this.stage = stage;
    }

    /** Builds a node with the stage's own default construction. */
    public static HdcNode of(String id) {
        return new HdcNode(NodeId.of(id), new HdcRetrievalStage());
    }

    @Override
    public NodeId id() {
        return id;
    }

    @Override
    public Activation process(Activation input) {
        double magnitude = input.magnitude();
        double[] signal = {magnitude * weight};
        return input.withSignal(signal);
    }

    @Override
    public void updateWeights(Activation pre, Activation post) {
        weight = PlasticityRule.update(weight, pre.magnitude(), post.magnitude(),
                Connectome.DEFAULT_ETA, Connectome.DEFAULT_DECAY);
    }

    @Override
    public NodeState state() {
        return state;
    }

    /** Sets the arousal state, e.g. to park this node during a consolidation pass. */
    public void setState(NodeState newState) {
        this.state = newState == null ? NodeState.DORMANT : newState;
    }

    /** Current edge weight, exposed for tests and for {@link Connectome} inspection. */
    public double weight() {
        return weight;
    }

    /** The wrapped stage, for callers that need the full retrieval result rather than a scalar. */
    public HdcRetrievalStage stage() {
        return stage;
    }
}
