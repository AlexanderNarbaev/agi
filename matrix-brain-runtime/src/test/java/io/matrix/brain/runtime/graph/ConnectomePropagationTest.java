package io.matrix.brain.runtime.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** W37.1 — Top-K routing, determinism, and node lifecycle. */
class ConnectomePropagationTest {

    /** Emits a fixed magnitude and counts how often it was invoked. */
    private static final class CountingNode implements CognitiveNode {
        private final NodeId id;
        private final double magnitude;
        private final AtomicInteger calls = new AtomicInteger();
        private CognitiveNode.NodeState state = CognitiveNode.NodeState.ACTIVE;

        CountingNode(String id, double magnitude) {
            this.id = NodeId.of(id);
            this.magnitude = magnitude;
        }

        @Override public NodeId id() { return id; }
        @Override public NodeState state() { return state; }
        void setState(CognitiveNode.NodeState s) { this.state = s; }
        int calls() { return calls.get(); }

        @Override
        public Activation process(Activation in) {
            calls.incrementAndGet();
            return in.withSignal(new double[] {magnitude});
        }
    }

    @Test
    @DisplayName("Top-K limits how many outgoing edges a node follows")
    void topKBoundsFanOut() {
        Connectome c = new Connectome();
        CountingNode src = new CountingNode("src", 1.0);
        c.add(src);
        for (String t : List.of("a", "b", "c", "d")) {
            c.add(new CountingNode(t, 1.0));
            c.connect(NodeId.of("src"), NodeId.of(t), 0.5);
        }

        Activation out = c.propagate(Activation.of(1.0), 2);

        assertThat(out.signal()).hasSize(2);
    }

    @Test
    @DisplayName("Top-K of 1 follows only the strongest edge")
    void topKOneFollowsStrongest() {
        Connectome c = new Connectome();
        c.add(new CountingNode("src", 1.0));
        c.add(new CountingNode("weak", 1.0));
        c.add(new CountingNode("strong", 1.0));
        c.connect(NodeId.of("src"), NodeId.of("weak"), 0.1);
        c.connect(NodeId.of("src"), NodeId.of("strong"), 0.9);

        // Signal is accumulated per target; the weak edge contributes 0.1, strong 0.9.
        Activation out = c.propagate(Activation.of(1.0), 1);
        assertThat(out.signal()).hasSize(1);
        assertThat(out.signal()[0])
                .as("only the strongest edge should carry signal")
                .isEqualTo(0.9, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    @DisplayName("propagation is deterministic across repeated runs")
    void propagationIsDeterministic() {
        assertThat(tiedRun(List.of("z", "y", "x", "w")))
                .as("the same graph must yield the same activation every time")
                .isEqualTo(tiedRun(List.of("z", "y", "x", "w")));
    }

    /**
     * Ties everywhere, so only an explicit total ordering can keep this stable.
     *
     * <p>Two graphs are built that differ ONLY in the order the tied edges were added. A total
     * ordering by {@link NodeId} must produce the same selected targets from both; an unordered
     * or insertion-ordered sort produces different ones. Comparing two graphs inside one JVM is
     * the only way to catch this — running the same graph twice yields the same answer either way
     * because the iteration order is stable within a single {@code LinkedHashMap}.</p>
     */
    @Test
    @DisplayName("tied edges are broken by NodeId, not by insertion order")
    void tiesResolveByNodeId() {
        List<NodeId> forward = tiedRun(List.of("z", "y", "x", "w"));
        List<NodeId> reversed = tiedRun(List.of("w", "x", "y", "z"));
        assertThat(reversed)
                .as("insertion order must not decide which tied edges are followed")
                .isEqualTo(forward);
    }

    /** Builds a graph with four identically-weighted outgoing edges and returns what propagated. */
    private static List<NodeId> tiedRun(List<String> insertionOrder) {
        Connectome c = new Connectome();
        c.add(new CountingNode("src", 1.0));
        for (String t : insertionOrder) {
            c.add(new CountingNode(t, 1.0));
            c.connect(NodeId.of("src"), NodeId.of(t), 0.5); // identical weights -> pure ties
        }
        c.propagate(Activation.of(1.0), 2);
        // Compare WHICH nodes were reached, not the signal values: every tied edge contributes
        // 0.5, so the returned activation is identical no matter which two edges were followed.
        return new ArrayList<>(c.lastReached());
    }

    @Test
    @DisplayName("a DORMANT node does not participate in propagation")
    void dormantNodesAreSkipped() {
        Connectome c = new Connectome();
        CountingNode src = new CountingNode("src", 1.0);
        CountingNode dst = new CountingNode("dst", 1.0);
        c.add(src);
        c.add(dst);
        c.connect(NodeId.of("src"), NodeId.of("dst"), 1.0);

        assertThat(c.propagate(Activation.of(1.0), 1).signal()).hasSize(1);

        src.setState(CognitiveNode.NodeState.DORMANT);
        assertThat(c.propagate(Activation.of(1.0), 1).signal())
                .as("a dormant source must not emit")
                .containsExactly(0.0);
    }

    @Test
    @DisplayName("an INHIBITED destination is not activated")
    void inhibitedDestinationsAreSkipped() {
        Connectome c = new Connectome();
        c.add(new CountingNode("src", 1.0));
        CountingNode dst = new CountingNode("dst", 1.0);
        c.add(dst);
        c.connect(NodeId.of("src"), NodeId.of("dst"), 1.0);
        dst.setState(CognitiveNode.NodeState.INHIBITED);

        assertThat(c.propagate(Activation.of(1.0), 1).signal())
                .containsExactly(0.0);
    }

    @Test
    @DisplayName("topK below 1 is rejected rather than silently treated as unlimited")
    void topKMustBePositive() {
        Connectome c = new Connectome();
        c.add(new CountingNode("src", 1.0));
        assertThatThrownBy(() -> c.propagate(Activation.of(1.0), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");
    }

    @Test
    @DisplayName("pruned synapses do not carry signal")
    void prunedEdgesAreInert() {
        Connectome c = new Connectome();
        c.add(new CountingNode("src", 1.0));
        c.add(new CountingNode("dst", 1.0));
        Synapse s = c.connect(NodeId.of("src"), NodeId.of("dst"), 1.0)
                .synapses().get(0);
        assertThat(c.markPruned(s)).isTrue();

        assertThat(c.propagate(Activation.of(1.0), 1).signal()).containsExactly(0.0);
    }

    @Test
    @DisplayName("plasticity moves weights but keeps them inside [-1,1]")
    void plasticityKeepsWeightsBounded() {
        Connectome c = new Connectome();
        c.add(new CountingNode("src", 1.0));
        c.add(new CountingNode("dst", 1.0));
        c.connect(NodeId.of("src"), NodeId.of("dst"), 0.9);

        Activation in = Activation.of(5.0);
        Activation out = c.propagate(in, 1);
        for (int i = 0; i < 200; i++) c.applyPlasticity(in, out);

        double w = c.synapses().get(0).weight();
        assertThat(Math.abs(w)).isLessThanOrEqualTo(1.0);
    }
}
