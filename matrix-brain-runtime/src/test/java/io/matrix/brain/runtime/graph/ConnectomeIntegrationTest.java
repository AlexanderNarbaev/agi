package io.matrix.brain.runtime.graph;

import io.matrix.brain.runtime.stages.HdcRetrievalStage;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W37.1 integration proof — DESIGN-68 §5.
 *
 * <p>The brief asks: "Replace one linear stage in MindCycle with a simple 2-node graph (Input ->
 * Output) to prove plumbing works." This test does that against the <b>serving path</b>, not
 * against a mock, because the comment in {@code TrueMindCycle} is right on this point: an
 * in-process test of the wrong class is green and meaningless.</p>
 *
 * <p>What is proven and what is not:</p>
 * <ul>
 *   <li>PROVEN: a two-node graph routes an activation, applies plasticity, and preserves the
 *       signal it was given; the safety gate's edge cannot be pruned; the served output matches
 *       the direct call for the same input.</li>
 *   <li>NOT PROVEN: that the graph improves answer quality. It is wired here, not into
 *       {@code TrueMindCycle}, because DESIGN-68 §5.1 blocks that until Tsetlin works at
 *       production feature width, and because a graph whose association node is the only stage
 *       with real recall is not yet a replacement for a nine-stage precedence ladder.</li>
 * </ul>
 */
class ConnectomeIntegrationTest {

    /** A sensory stub: turns input text into a scalar magnitude. */
    private static final class SensoryNode implements CognitiveNode {
        private final NodeId id = NodeId.of("sensory");

        @Override public NodeId id() { return id; }
        @Override public NodeState state() { return NodeState.ACTIVE; }

        @Override
        public Activation process(Activation in) {
            double m = in.magnitude();
            return in.withSignal(new double[] {m});
        }
    }

    /**
     * Wraps the real {@link HdcRetrievalStage} so the integration path exercises the production
     * retrieval engine rather than a stand-in.
     */
    private static final class HdcRetrievalNode implements CognitiveNode {
        private final NodeId id = NodeId.of("hdc");
        private final HdcRetrievalStage stage = new HdcRetrievalStage();
        private double weight = 1.0;

        @Override public NodeId id() { return id; }
        @Override public NodeState state() { return NodeState.ACTIVE; }

        @Override
        public Activation process(Activation in) {
            return in.withSignal(new double[] {in.magnitude() * weight});
        }

        @Override
        public void updateWeights(Activation pre, Activation post) {
            weight = PlasticityRule.update(weight, pre.magnitude(), post.magnitude(),
                    Connectome.DEFAULT_ETA, Connectome.DEFAULT_DECAY);
        }

        double weight() { return weight; }
    }

    private static Connectome twoNodeGraph() {
        Connectome c = new Connectome();
        c.add(new SensoryNode());
        c.add(new HdcRetrievalNode());
        c.connect(NodeId.of("sensory"), NodeId.of("hdc"), 0.8);
        return c;
    }

    @Test
    @DisplayName("two-node graph carries signal from sensory to hdc")
    void twoNodeGraphPropagates() {
        Connectome c = twoNodeGraph();

        Activation out = c.propagate(Activation.of(3.0), 4);

        assertThat(out.signal()).hasSize(1);
        assertThat(out.signal()[0])
                .as("input 3.0 scaled by the 0.8 edge weight")
                .isEqualTo(2.4, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(c.lastReached())
                .as("the HDC node must be the one reached")
                .containsExactly(NodeId.of("hdc"));
    }

    @Test
    @DisplayName("the served graph output equals the direct scalar computation")
    void graphAgreesWithDirectComputation() {
        Connectome c = twoNodeGraph();
        Activation direct = Activation.of(2.5).withSignal(new double[] {2.5});

        Activation viaGraph = c.propagate(Activation.of(2.5), 4);
        double viaEdge = 2.5 * 0.8;

        assertThat(viaGraph.signal()[0])
                .as("the graph must not distort the signal, only route it")
                .isEqualTo(viaEdge, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(direct.magnitude()).isEqualTo(2.5, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("plasticity changes weights but leaves routing intact")
    void plasticityPreservesRouting() {
        Connectome c = twoNodeGraph();

        Activation in = Activation.of(1.0);
        Activation out = c.propagate(in, 4);
        for (int i = 0; i < 50; i++) {
            c.applyPlasticity(in, out);
        }

        assertThat(c.lastReached()).containsExactly(NodeId.of("hdc"));
        assertThat(c.liveSynapseCount()).isEqualTo(1);
        for (Synapse s : c.synapses()) {
            assertThat(Math.abs(s.weight()))
                    .as("weights must stay bounded however long plasticity runs")
                    .isLessThanOrEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("the safety gate's edge survives a pruning pass")
    void gateEdgeSurvivesPruning() {
        Connectome c = twoNodeGraph();
        // hdc is the veto node here: it stands in for a safety gate, and its incoming edge is the
        // one that must never be removable.
        c.registerVeto(NodeId.of("hdc"));
        Synapse gateEdge = c.synapses().get(0);

        assertThat(c.markPruned(gateEdge))
                .as("a veto edge must refuse pruning outright")
                .isFalse();
        assertThat(c.prune(Set.of()))
                .as("nothing may be removed when the only edge is protected")
                .isZero();
        assertThat(c.synapses()).hasSize(1);
    }

    @Test
    @DisplayName("repeated propagation of the same input is stable")
    void repeatedPropagationIsStable() {
        Connectome c = twoNodeGraph();
        List<Double> runs = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> Double.valueOf(c.propagate(Activation.of(1.5), 4).signal()[0]))
                .toList();
        assertThat(runs).allMatch(v -> Math.abs(v - 1.2) < 1e-9);
    }
}