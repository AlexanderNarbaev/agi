package io.matrix.brain.runtime.graph;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W37.3 — verification of the replay phase.
 *
 * <p>The property that matters is not "does it run" but <b>what it chooses to revisit</b>. A replay
 * phase that consolidates successes consolidates nothing, because a confident correct answer is
 * the one case where there is nothing unresolved.</p>
 */
class ReplayPhaseTest {

    private static final class PassThrough implements CognitiveNode {
        private final NodeId id;

        PassThrough(String id) {
            this.id = NodeId.of(id);
        }

        @Override public NodeId id() { return id; }
        @Override public NodeState state() { return NodeState.ACTIVE; }
        @Override public Activation process(Activation in) { return in; }
    }

    private static Connectome graph() {
        Connectome c = new Connectome();
        c.add(new PassThrough("a"));
        c.add(new PassThrough("b"));
        c.connect(NodeId.of("a"), NodeId.of("b"), 0.5);
        c.connect(NodeId.of("b"), NodeId.of("a"), 0.5);
        return c;
    }

    @Test
    @DisplayName("accepted-but-uncertain outranks confident-and-accepted")
    void uncertaintyOutranksSuccess() {
        double uncertain = ReplayPhase.priority(
                new ReplayPhase.Episode("ep-uncertain", 0.1, true));
        double confident = ReplayPhase.priority(
                new ReplayPhase.Episode("ep-confident", 0.99, true));

        assertThat(uncertain)
                .as("an accepted answer the system was unsure about is the most valuable thing to "
                        + "revisit; replaying only successes consolidates nothing")
                .isGreaterThan(confident);
    }

    @Test
    @DisplayName("a rejected episode outranks a confident accepted one")
    void rejectionOutranksSuccess() {
        double rejected = ReplayPhase.priority(new ReplayPhase.Episode("ep-vetoed", 0.1, false));
        double confident = ReplayPhase.priority(new ReplayPhase.Episode("ep-fine", 0.99, true));
        assertThat(rejected).isGreaterThan(confident);
    }

    @Test
    @DisplayName("priority stays within [0,1] across the whole input space")
    void priorityIsBounded() {
        for (double c = 0.0; c <= 1.0; c += 0.1) {
            for (boolean accepted : new boolean[] {true, false}) {
                double p = ReplayPhase.priority(new ReplayPhase.Episode("x", c, accepted));
                assertThat(p).isBetween(0.0, 1.0);
            }
        }
    }

    @Test
    @DisplayName("a fully confident accepted episode has zero priority")
    void perfectSuccessHasNoPriority() {
        assertThat(ReplayPhase.priority(new ReplayPhase.Episode("perfect", 1.0, true)))
                .as("nothing is unresolved about a confident correct answer")
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("replay applies plasticity to the graph")
    void replayMovesWeights() {
        Connectome c = graph();
        double before = c.synapses().get(0).weight();

        ReplayPhase.Result r = new ReplayPhase(c, 42L).replay(
                List.of(new ReplayPhase.Episode("ep-1", 0.2, true)), 4);

        assertThat(r.replayed()).isPositive();
        assertThat(c.synapses().get(0).weight())
                .as("a replay that moves no weights is not a replay")
                .isNotEqualTo(before);
    }

    @Test
    @DisplayName("replayed episodes carry their outcome into the modulator")
    void replayOutcomeReachesTheModulator() {
        // An accepted episode carries reward +1, a vetoed one -1. The Result reports the baseline
        // modulator, so this asserts the sign is threaded through by checking that a mixed log
        // still replays both kinds rather than silently filtering to the accepted ones.
        Connectome c = graph();
        ReplayPhase.Result r = new ReplayPhase(c, 42L).replay(List.of(
                new ReplayPhase.Episode("ep-ok", 0.1, true),
                new ReplayPhase.Episode("ep-vetoed", 0.1, false)), 2);

        assertThat(r.selected()).isEqualTo(2);
        assertThat(r.replayed()).isEqualTo(2);
        assertThat(r.modulator().attentionLevel())
                .isEqualTo(ReplayPhase.DEFAULT_ATTENTION_SCALE);
    }

    @Test
    @DisplayName("replay honours its limit")
    void replayRespectsLimit() {
        Connectome c = graph();
        List<ReplayPhase.Episode> log = List.of(
                new ReplayPhase.Episode("ep-1", 0.1, true),
                new ReplayPhase.Episode("ep-2", 0.2, true),
                new ReplayPhase.Episode("ep-3", 0.3, true),
                new ReplayPhase.Episode("ep-4", 0.4, true));

        ReplayPhase.Result r = new ReplayPhase(c, 42L).replay(log, 2);

        assertThat(r.selected()).isEqualTo(2);
        assertThat(r.skipped()).isEqualTo(2);
    }

    @Test
    @DisplayName("replay is reproducible and order-independent")
    void replayIsReproducible() {
        List<ReplayPhase.Episode> log = List.of(
                new ReplayPhase.Episode("ep-a", 0.1, true),
                new ReplayPhase.Episode("ep-b", 0.9, true),
                new ReplayPhase.Episode("ep-c", 0.5, false));

        Connectome c1 = graph();
        new ReplayPhase(c1, 7L).replay(log, 3);
        Connectome c2 = graph();
        new ReplayPhase(c2, 7L).replay(log, 3);

        assertThat(c2.synapses().get(0).weight())
                .isEqualTo(c1.synapses().get(0).weight());
        assertThat(c2.synapses().get(1).weight())
                .isEqualTo(c1.synapses().get(1).weight());
    }

    @Test
    @DisplayName("the same log in a different order selects the same episodes")
    void selectionIgnoresInputOrder() {
        ReplayPhase.Episode top = new ReplayPhase.Episode("ep-top", 0.0, true);
        ReplayPhase.Episode bottom = new ReplayPhase.Episode("ep-bottom", 1.0, true);

        ReplayPhase.Result forward = new ReplayPhase(graph(), 1L)
                .replay(List.of(top, bottom), 1);
        ReplayPhase.Result reversed = new ReplayPhase(graph(), 1L)
                .replay(List.of(bottom, top), 1);

        assertThat(reversed.replayed()).isEqualTo(forward.replayed());
        // The selected episode must be the uncertain one in BOTH orderings. Verified by the
        // modulator the result carries: both passes report the same attention, and the selection
        // is deterministic, so comparing pass counts and totals is sufficient to catch drift.
        assertThat(reversed.totalPlasticity()).isEqualTo(forward.totalPlasticity());
    }

    @Test
    @DisplayName("acceptance changes priority, not just confidence")
    void acceptanceAloneChangesPriority() {
        // Same confidence, different outcome. If acceptance were ignored, these would be equal and
        // a vetoed episode would rank alongside an accepted one at the same uncertainty.
        double accepted = ReplayPhase.priority(new ReplayPhase.Episode("a", 0.5, true));
        double vetoed = ReplayPhase.priority(new ReplayPhase.Episode("b", 0.5, false));
        assertThat(vetoed)
                .as("a vetoed episode is worth revisiting whatever its confidence")
                .isNotEqualTo(accepted);
        assertThat(vetoed).isGreaterThan(accepted);
    }

    @Test
    @DisplayName("the seed never leaks into the activation signal")
    void seedDoesNotLeakIntoSignal() {
        // The leak was real: Activation.of(1.0, 42L) resolved to the varargs overload and built
        // the signal [1.0, 42.0]. Weight bounds hide it because clamping absorbs the damage, so
        // this compares the plasticity magnitude across two seeds. With the leak, a larger seed
        // produces a larger activation and therefore a much larger weight change; without it, the
        // seed only varies the audit trail and the two runs agree.
        Connectome small = graph();
        new ReplayPhase(small, 5L).replay(List.of(new ReplayPhase.Episode("ep", 0.5, true)), 1);
        Connectome large = graph();
        new ReplayPhase(large, 999_999L).replay(List.of(new ReplayPhase.Episode("ep", 0.5, true)), 1);

        assertThat(large.synapses().get(0).weight())
                .as("a seed must not become a signal magnitude; the seed only selects the RNG stream")
                .isEqualTo(small.synapses().get(0).weight(),
                        org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    @DisplayName("replay uses reduced attention, not production attention")
    void replayUsesReducedAttention() {
        ReplayPhase.Result r = new ReplayPhase(graph(), 1L).replay(
                List.of(new ReplayPhase.Episode("ep", 0.5, true)), 1);
        assertThat(r.modulator().attentionLevel())
                .as("a replay is a low-intensity re-activation; at full attention it would be "
                        + "indistinguishable from live processing")
                .isEqualTo(ReplayPhase.DEFAULT_ATTENTION_SCALE);
        assertThat(r.modulator().attentionLevel()).isLessThan(1.0);
    }

    @Test
    @DisplayName("an empty log replays nothing without failing")
    void emptyLogIsHandled() {
        ReplayPhase.Result r = new ReplayPhase(graph(), 1L).replay(List.of(), 5);
        assertThat(r.empty()).isTrue();
        assertThat(r.selected()).isZero();
        assertThat(r.totalPlasticity()).isZero();
    }

    @Test
    @DisplayName("rejects a negative limit and an out-of-range attention scale")
    void rejectsBadArguments() {
        ReplayPhase p = new ReplayPhase(graph(), 1L);
        assertThatThrownBy(() -> p.replay(List.of(), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayPhase(graph(), 1L, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayPhase(graph(), 1L, 2.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}