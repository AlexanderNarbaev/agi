package io.matrix.brain.runtime.graph;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W37.3 — verification of the pruning phase against the brief's PASS criterion:
 * "Network shrinks or stabilizes; accuracy does not drop."
 *
 * <p>The evaluator here is a real, closed-form function of the graph rather than a mock that
 * returns a constant, so a pruner that damages a load-bearing edge is caught by the accuracy
 * falling rather than by a test asserting a boolean.</p>
 */
class SleepConsolidatorTest {

    /** A node that forwards its input unchanged. */
    private static final class PassThrough implements CognitiveNode {
        private final NodeId id;

        PassThrough(String id) {
            this.id = NodeId.of(id);
        }

        @Override public NodeId id() { return id; }
        @Override public NodeState state() { return NodeState.ACTIVE; }
        @Override public Activation process(Activation in) { return in; }
    }

    /**
     * Accuracy model: the graph must preserve a strong path {@code src -> loadBearing -> out}.
     * Removing the strong edge drops accuracy to 0; removing the weak decorative edge does not.
     */
    private static double heldOutAccuracy(Connectome c) {
        for (Synapse s : c.synapses()) {
            if (s.from().equals(NodeId.of("src")) && s.to().equals(NodeId.of("loadBearing"))) {
                return s.state() == Synapse.SynapseState.PRUNED ? 0.0 : 1.0;
            }
        }
        return 0.0;
    }

    private static Connectome graph() {
        Connectome c = new Connectome();
        c.add(new PassThrough("src"));
        c.add(new PassThrough("loadBearing"));
        c.add(new PassThrough("decorative"));
        c.add(new PassThrough("out"));
        // The load-bearing edge is strong, so a lowest-|weight| pruner targets the other one.
        c.connect(NodeId.of("src"), NodeId.of("loadBearing"), 0.9);
        c.connect(NodeId.of("src"), NodeId.of("decorative"), 0.01);
        c.connect(NodeId.of("decorative"), NodeId.of("out"), 0.02);
        return c;
    }

    @Test
    @DisplayName("a safe prune is accepted: graph shrinks and accuracy is unchanged")
    void safePruneIsAccepted() {
        Connectome c = graph();
        SleepConsolidator.Result r =
                new SleepConsolidator(c, SleepConsolidatorTest::heldOutAccuracy, 42L)
                        .consolidate(0.34, 0.0);

        System.out.printf("  before=%d after=%d acc %.1f -> %.1f accepted=%s%n",
                r.synapsesBefore(), r.synapsesAfter(), r.accuracyBefore(), r.accuracyAfter(),
                r.accepted());

        assertThat(r.accepted()).isTrue();
        assertThat(r.removed())
                .as("the two decorative edges are the weakest and should go")
                .isEqualTo(2);
        assertThat(r.metPassCriterion())
                .as("the brief's criterion: shrinks AND accuracy does not drop")
                .isTrue();
        assertThat(r.accuracyAfter()).isEqualTo(r.accuracyBefore());
    }

    @Test
    @DisplayName("a damaging prune is rolled back entirely")
    void damagingPruneIsRejected() {
        // Only one edge, and it is the one that matters. Any proposal must be rejected.
        Connectome c = new Connectome();
        c.add(new PassThrough("src"));
        c.add(new PassThrough("loadBearing"));
        c.connect(NodeId.of("src"), NodeId.of("loadBearing"), 0.9);

        SleepConsolidator.Result r =
                new SleepConsolidator(c, SleepConsolidatorTest::heldOutAccuracy, 42L)
                        .consolidate(1.0, 0.0);

        System.out.printf("  damaging: before=%d after=%d acc %.1f -> %.1f accepted=%s%n",
                r.synapsesBefore(), r.synapsesAfter(), r.accuracyBefore(), r.accuracyAfter(),
                r.accepted());

        assertThat(r.accepted()).isFalse();
        assertThat(r.synapsesAfter())
                .as("a rejected pass must restore the graph exactly, not partially")
                .isEqualTo(r.synapsesBefore());
        assertThat(r.accuracyAfter()).isEqualTo(r.accuracyBefore());
        assertThat(r.metPassCriterion()).isFalse();
    }

    @Test
    @DisplayName("veto edges are never proposed for pruning")
    void vetoEdgesAreNeverCandidates() {
        Connectome c = graph();
        c.registerVeto(NodeId.of("loadBearing"));
        // Now the only removable edges are the decorative ones, even at 100% proposal.
        SleepConsolidator.Result r =
                new SleepConsolidator(c, SleepConsolidatorTest::heldOutAccuracy, 42L)
                        .consolidate(1.0, 0.0);

        assertThat(c.synapses())
                .as("the veto edge must survive a maximal pruning proposal")
                .anyMatch(s -> s.to().equals(NodeId.of("loadBearing"))
                        && s.state() == Synapse.SynapseState.CONNECTED);
        assertThat(r.removed()).isEqualTo(2);
    }

    /**
     * Defence in depth: the veto edge survives even if the consolidator's own filter were removed.
     *
     * <p>A mutation that deletes the {@code isVeto} filter from the candidate list survives every
     * other test, because {@link Connectome#prune} re-checks the exemption when the graph is
     * actually shrunk. That is the intended layering — but it means the filter itself is untested,
     * so this test asserts the <b>outcome</b> rather than the mechanism, which is the property
     * that actually matters: no consolidation path can remove a veto edge.</p>
     */
    @Test
    @DisplayName("veto edges survive consolidation regardless of which layer enforces it")
    void vetoEdgeSurvivesRegardlessOfEnforcingLayer() {
        Connectome c = new Connectome();
        c.add(new PassThrough("src"));
        c.add(new PassThrough("gate"));
        c.add(new PassThrough("spare"));
        c.registerVeto(NodeId.of("gate"));
        c.connect(NodeId.of("src"), NodeId.of("gate"), 0.9);   // veto target, STRONGEST
        c.connect(NodeId.of("src"), NodeId.of("spare"), 0.001); // weakest, not a candidate outcome

        // fraction 1.0 proposes EVERY non-pruned edge, including the veto one if unfiltered.
        SleepConsolidator.Result r =
                new SleepConsolidator(c, g -> 1.0, 7L).consolidate(1.0, 0.0);

        assertThat(c.synapses())
                .as("a maximal proposal must still leave the veto edge present and connected")
                .anyMatch(s -> s.to().equals(NodeId.of("gate"))
                        && s.state() == Synapse.SynapseState.CONNECTED);
        assertThat(c.liveSynapseCount())
                .as("only the non-veto edge may go")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("consolidation is reproducible from its seed")
    void consolidationIsReproducible() {
        long[] first = runTwice(42L);
        long[] second = runTwice(42L);
        assertThat(first).isEqualTo(second);
    }

    private static long[] runTwice(long seed) {
        Connectome c = graph();
        SleepConsolidator.Result r =
                new SleepConsolidator(c, SleepConsolidatorTest::heldOutAccuracy, seed)
                        .consolidate(0.5, 0.0);
        return new long[] {r.synapsesAfter(), r.accepted() ? 1 : 0};
    }

    @Test
    @DisplayName("rejects an out-of-range proposal fraction")
    void rejectsBadFraction() {
        Connectome c = graph();
        SleepConsolidator s = new SleepConsolidator(c, SleepConsolidatorTest::heldOutAccuracy, 1L);
        assertThatThrownBy(() -> s.consolidate(0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fraction");
        assertThatThrownBy(() -> s.consolidate(1.5, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a graph with nothing to prune reports zero removed")
    void emptyGraphIsHandled() {
        Connectome c = new Connectome();
        c.add(new PassThrough("a"));
        SleepConsolidator.Result r =
                new SleepConsolidator(c, g -> 1.0, 1L).consolidate(0.5, 0.0);
        assertThat(r.removed()).isZero();
        assertThat(r.metPassCriterion()).isFalse();
        assertThat(r.synapsesBefore()).isZero();
    }
}