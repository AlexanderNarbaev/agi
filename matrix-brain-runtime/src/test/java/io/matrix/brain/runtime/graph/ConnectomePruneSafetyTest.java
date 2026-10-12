package io.matrix.brain.runtime.graph;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W37.1 — the structural safety invariant of {@link Connectome#prune}.
 *
 * <p><b>Why this test exists.</b> Pruning is how sleep-time consolidation shrinks the graph. If an
 * edge into a safety gate could be pruned, accumulated pressure would eventually disable the gate
 * and the system would "learn" to stop checking itself. DESIGN-68 §4.1 requires veto edges to be
 * structurally exempt, not exempt by convention — and a convention enforced by callers fails the
 * first time an unanticipated call path prunes.</p>
 *
 * <p>The test asserts both directions: a protected edge survives, and an ordinary edge is removed
 * <b>in the same call</b>. A prune that protected everything would satisfy a survival-only
 * assertion, and a prune that removed everything would satisfy a removal-only one.</p>
 */
class ConnectomePruneSafetyTest {

    /** A node that echoes its input; enough to satisfy the interface without real behaviour. */
    private static final class EchoNode implements CognitiveNode {
        private final NodeId id;
        private NodeState state = NodeState.ACTIVE;

        EchoNode(String id) {
            this.id = NodeId.of(id);
        }

        @Override public NodeId id() { return id; }
        @Override public Activation process(Activation in) { return in; }
        @Override public NodeState state() { return state; }
    }

    private static Connectome threeNodeGraph() {
        Connectome c = new Connectome();
        c.add(new EchoNode("sensory"));
        c.add(new EchoNode("gate"));
        c.add(new EchoNode("output"));
        c.connect(NodeId.of("sensory"), NodeId.of("gate"), 0.5);
        c.connect(NodeId.of("gate"), NodeId.of("output"), 0.5);
        return c;
    }

    @Test
    @DisplayName("prune removes an ordinary pruned edge")
    void pruneRemovesOrdinaryEdge() {
        Connectome c = threeNodeGraph();
        Synapse ordinary = c.synapses().get(1); // gate -> output, no veto registered
        assertThat(c.markPruned(ordinary)).isTrue();

        int removed = c.prune(Set.of());

        assertThat(removed)
                .as("an unprotected pruned edge must actually be removed")
                .isEqualTo(1);
        assertThat(c.synapses())
                .as("only the ordinary edge should be gone")
                .hasSize(1);
    }

    @Test
    @DisplayName("prune NEVER removes an edge into a veto node")
    void pruneNeverRemovesVetoEdge() {
        Connectome c = threeNodeGraph();
        c.registerVeto(NodeId.of("gate"));

        // Try to prune BOTH edges.
        assertThat(c.markPruned(c.synapses().get(0))).as("veto edge must refuse pruning outright").isFalse();
        assertThat(c.markPruned(c.synapses().get(1))).as("veto edge must refuse pruning outright").isFalse();

        int removed = c.prune(Set.of());

        assertThat(removed).isZero();
        assertThat(c.synapses())
                .as("both edges touch a veto node, so both must survive")
                .hasSize(2);
    }

    @Test
    @DisplayName("veto protection holds alongside a real removal in the same call")
    void vetoProtectionHoldsWithMixedEdges() {
        Connectome c = new Connectome();
        c.add(new EchoNode("a"));
        c.add(new EchoNode("b"));   // veto
        c.add(new EchoNode("c"));
        c.add(new EchoNode("d"));
        c.registerVeto(NodeId.of("b"));
        c.connect(NodeId.of("a"), NodeId.of("b"), 0.9);  // target is veto  -> protected
        c.connect(NodeId.of("b"), NodeId.of("c"), 0.9);  // source is veto  -> protected
        c.connect(NodeId.of("c"), NodeId.of("d"), 0.9);  // ordinary        -> removable

        assertThat(c.markPruned(c.synapses().get(0))).as("veto target refuses").isFalse();
        assertThat(c.markPruned(c.synapses().get(1))).as("veto source refuses").isFalse();
        assertThat(c.markPruned(c.synapses().get(2))).as("ordinary edge prunes").isTrue();

        int removed = c.prune(Set.of());

        assertThat(removed)
                .as("exactly the ordinary edge is removable, even with veto edges present")
                .isEqualTo(1);
        assertThat(c.synapses())
                .as("both veto-touching edges survive")
                .hasSize(2);
        assertThat(c.synapses()).extracting(Synapse::to)
                .containsExactly(NodeId.of("b"), NodeId.of("c"));
    }

    @Test
    @DisplayName("prune also honours a caller-supplied exemption set")
    void pruneHonoursExplicitExemptions() {
        Connectome c = threeNodeGraph();
        Synapse ordinary = c.synapses().get(1);
        assertThat(c.markPruned(ordinary)).isTrue();

        int removed = c.prune(Set.of(NodeId.of("output")));

        assertThat(removed)
                .as("the exemption set protects output even without registerVeto")
                .isZero();
        assertThat(c.synapses()).hasSize(2);
    }

    @Test
    @DisplayName("registering a veto after edges exist still protects them")
    void lateRegistrationStillProtects() {
        Connectome c = threeNodeGraph();
        assertThat(c.synapses()).hasSize(2);
        c.registerVeto(NodeId.of("output"));
        assertThat(c.markPruned(c.synapses().get(1))).isFalse();
        assertThat(c.prune(Set.of())).isZero();
    }

    @Test
    @DisplayName("connect rejects unknown endpoints rather than creating a dangling edge")
    void connectRejectsUnknownNodes() {
        Connectome c = new Connectome();
        c.add(new EchoNode("only"));
        assertThatThrownBy(() -> c.connect(NodeId.of("only"), NodeId.of("ghost"), 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghost");
    }
}