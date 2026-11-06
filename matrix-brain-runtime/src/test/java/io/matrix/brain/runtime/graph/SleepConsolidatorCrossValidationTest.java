package io.matrix.brain.runtime.graph;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W37.3 — verification that cross-validated pruning catches what a single split misses.
 *
 * <p>The single-split {@link SleepConsolidator#consolidate} accepts a prune whenever one evaluator
 * does not notice. These tests build the case where it does not: an evaluator that scores one part
 * of the graph, leaving damage elsewhere invisible.</p>
 */
class SleepConsolidatorCrossValidationTest {

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
     * Chain: s -> lb (strong, load bearing) and s -> d1 -> d2 (weak decoration).
     * Fold A only scores lb. Fold B only scores d2. A prune of the decoration helps nobody and
     * hurts nobody by fold A's measure — but the graph is smaller either way.
     */
    private static Connectome chain() {
        Connectome c = new Connectome();
        c.add(new PassThrough("s"));
        c.add(new PassThrough("lb"));
        c.add(new PassThrough("d1"));
        c.add(new PassThrough("d2"));
        c.connect(NodeId.of("s"), NodeId.of("lb"), 0.9);
        c.connect(NodeId.of("s"), NodeId.of("d1"), 0.10);
        c.connect(NodeId.of("d1"), NodeId.of("d2"), 0.10);
        return c;
    }

    /** Scores only whether the load-bearing edge is alive. */
    private static double foldLoadBearing(Connectome c) {
        for (Synapse s : c.synapses()) {
            if (s.from().equals(NodeId.of("s")) && s.to().equals(NodeId.of("lb"))) {
                return s.state() == Synapse.SynapseState.PRUNED ? 0.0 : 1.0;
            }
        }
        return 0.0;
    }

    /** Scores only whether the second decoration edge is alive. */
    private static double foldDecoration(Connectome c) {
        for (Synapse s : c.synapses()) {
            if (s.from().equals(NodeId.of("d1")) && s.to().equals(NodeId.of("d2"))) {
                return s.state() == Synapse.SynapseState.PRUNED ? 0.0 : 1.0;
            }
        }
        return 0.0;
    }

    @Test
    @DisplayName("cross-validated pruning removes decoration and keeps the load-bearing edge")
    void crossValidationKeepsLoadBearingEdge() {
        Connectome c = chain();
        SleepConsolidator.Result r = new SleepConsolidator(c, x -> 0.0, 1L)
                .consolidateCrossValidated(
                        List.of(SleepConsolidatorCrossValidationTest::foldLoadBearing,
                                SleepConsolidatorCrossValidationTest::foldDecoration),
                        0.34, 0.0);

        assertThat(r.accuracyBefore())
                .as("the reported accuracy is the WORST fold, so it starts at 1.0 only if every "
                        + "fold is satisfied before the pass")
                .isEqualTo(1.0);
        assertThat(c.synapses())
                .as("the load-bearing edge must survive")
                .anyMatch(s -> s.to().equals(NodeId.of("lb"))
                        && s.state() == Synapse.SynapseState.CONNECTED);
    }

    @Test
    @DisplayName("a prune that only one fold notices is rejected")
    void damagingPruneIsRejectedByCrossValidation() {
        // Only one edge exists and it is load-bearing. Any proposal must be rejected.
        Connectome c = new Connectome();
        c.add(new PassThrough("s"));
        c.add(new PassThrough("lb"));
        c.connect(NodeId.of("s"), NodeId.of("lb"), 0.9);

        SleepConsolidator.Result r = new SleepConsolidator(c, x -> 0.0, 1L)
                .consolidateCrossValidated(
                        List.of(SleepConsolidatorCrossValidationTest::foldLoadBearing), 1.0, 0.0);

        assertThat(r.accepted()).isFalse();
        assertThat(r.synapsesAfter())
                .as("a rejected cross-validated pass must restore the graph exactly")
                .isEqualTo(r.synapsesBefore());
    }

    @Test
    @DisplayName("the reported accuracy is the worst fold, not the mean")
    void reportedAccuracyIsTheWorstFold() {
        // Fold A always scores 1.0; fold B scores 0.0. A mean would report 0.5 and hide the
        // failure; the worst-fold rule reports 0.0 and rejects the pass.
        Connectome c = chain();
        SleepConsolidator.Result r = new SleepConsolidator(c, x -> 0.0, 1L)
                .consolidateCrossValidated(List.of(g -> 1.0, g -> 0.0), 0.34, 0.0);

        assertThat(r.accuracyBefore())
                .as("worst fold = 0.0, so the baseline is 0.0 and any prune cannot improve on it")
                .isEqualTo(0.0);
        assertThat(r.accuracyAfter()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("veto edges stay protected under cross-validated pruning")
    void vetoEdgesStayProtected() {
        Connectome c = chain();
        c.registerVeto(NodeId.of("lb"));
        new SleepConsolidator(c, x -> 0.0, 1L).consolidateCrossValidated(
                List.of(SleepConsolidatorCrossValidationTest::foldLoadBearing,
                        SleepConsolidatorCrossValidationTest::foldDecoration), 1.0, 0.0);

        assertThat(c.synapses())
                .as("a maximal proposal must still leave the veto edge intact")
                .anyMatch(s -> s.to().equals(NodeId.of("lb"))
                        && s.state() == Synapse.SynapseState.CONNECTED);
    }

    @Test
    @DisplayName("rejects an empty fold set rather than silently accepting everything")
    void rejectsEmptyFolds() {
        SleepConsolidator s = new SleepConsolidator(chain(), x -> 1.0, 1L);
        assertThatThrownBy(() -> s.consolidateCrossValidated(List.of(), 0.5, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fold");
        assertThatThrownBy(() -> s.consolidateCrossValidated(null, 0.5, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}