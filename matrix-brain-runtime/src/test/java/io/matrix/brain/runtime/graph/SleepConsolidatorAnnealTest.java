package io.matrix.brain.runtime.graph;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W38.3 — S5.2 verification for {@link SleepConsolidator#annealConsolidate}.
 *
 * <p>The anneal explores on {@code markPruned}/{@code restore} only and calls
 * {@link Connectome#prune} exactly once at the very end, because {@code restore} cannot
 * resurrect an edge that {@code prune} already removed. See the class javadoc.</p>
 *
 * <p>The load-bearing property is the BEST-STATE GUARD: the returned graph is the best state
 * visited, never the last one. A Metropolis chain at high temperature can accept a damaging
 * move; without the guard that damage would be shipped into a safety-relevant graph.</p>
 */
class SleepConsolidatorAnnealTest {

    private static final class PassThrough implements CognitiveNode {
        private final NodeId id;

        PassThrough(String id) {
            this.id = NodeId.of(id);
        }

        @Override public NodeId id() { return id; }
        @Override public NodeState state() { return NodeState.ACTIVE; }
        @Override public Activation process(Activation in) { return in; }
    }

    private static boolean connected(Connectome c, String from, String to) {
        NodeId f = NodeId.of(from);
        NodeId t = NodeId.of(to);
        return c.synapses().stream().anyMatch(s ->
                s.from().equals(f) && s.to().equals(t)
                        && s.state() != Synapse.SynapseState.PRUNED);
    }

    /**
     * Keeping {@code src -> loadBearing} intact is worth 1.0. Every surviving junk edge out of
     * {@code src} costs 0.1. So pruning all four junk edges scores 1.0 and cutting the
     * load-bearing edge collapses the score to 0.0 or below.
     */
    private static double annealAccuracy(Connectome c) {
        double score = connected(c, "src", "loadBearing") ? 1.0 : 0.0;
        long junkLive = c.synapses().stream()
                .filter(s -> s.state() != Synapse.SynapseState.PRUNED)
                .filter(s -> s.from().equals(NodeId.of("src")))
                .filter(s -> !s.to().equals(NodeId.of("loadBearing")))
                .count();
        return score - 0.1 * junkLive;
    }

    private static final List<String> JUNK = List.of("j1", "j2", "j3", "j4");

    private static Connectome annealGraph() {
        Connectome c = new Connectome();
        c.add(new PassThrough("src"));
        c.add(new PassThrough("loadBearing"));
        JUNK.forEach(n -> c.add(new PassThrough(n)));
        c.connect(NodeId.of("src"), NodeId.of("loadBearing"), 0.90);
        double w = 0.20;
        for (String n : JUNK) {
            c.connect(NodeId.of("src"), NodeId.of(n), w);
            w += 0.01;
        }
        return c;
    }

    /**
     * Edges that SURVIVE the pass.
     *
     * <p>Deliberately not "edges in PRUNED state": {@link Connectome#prune} REMOVES those from
     * the synapse list, so a PRUNED-state scan is empty once the pass has finished and would make
     * every assertion below vacuous. That was a real defect in the first draft of this file.</p>
     */
    private static List<String> liveEdges(Connectome c) {
        List<String> out = new ArrayList<>();
        for (Synapse s : c.synapses()) {
            if (s.state() != Synapse.SynapseState.PRUNED) {
                out.add(s.from() + "->" + s.to());
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    private static SleepConsolidator.Result anneal(Connectome c, long seed,
                                                   double startT, double endT,
                                                   int sweeps, int restarts) {
        return new SleepConsolidator(c, SleepConsolidatorAnnealTest::annealAccuracy, seed)
                .annealConsolidate(0.25, startT, endT, sweeps, restarts);
    }

    @Test
    @DisplayName("the returned state is never worse than the entry state")
    void annealNeverReturnsSomethingWorseThanEntry() {
        Connectome c = annealGraph();
        SleepConsolidator.Result r = anneal(c, 42L, 5.0, 0.01, 12, 3);

        System.out.printf("  never-worse: before=%d after=%d acc %.3f -> %.3f live=%s%n",
                r.synapsesBefore(), r.synapsesAfter(), r.accuracyBefore(), r.accuracyAfter(),
                liveEdges(c));

        assertThat(r.accuracyAfter())
                .as("best-state guard: never worse than the state we started from")
                .isGreaterThanOrEqualTo(r.accuracyBefore());
        assertThat(r.accuracyBefore()).isEqualTo(0.6);
        assertThat(c.liveSynapseCount()).isLessThan(r.synapsesBefore());
    }

    @Test
    @DisplayName("the same seed reproduces the same anneal exactly")
    void annealIsReproducibleFromItsSeed() {
        Connectome a = annealGraph();
        SleepConsolidator.Result ra = anneal(a, 7L, 5.0, 0.01, 12, 3);
        Connectome b = annealGraph();
        SleepConsolidator.Result rb = anneal(b, 7L, 5.0, 0.01, 12, 3);

        assertThat(a.liveSynapseCount())
                .as("the repro assertion is only meaningful if the anneal actually pruned")
                .isLessThan(ra.synapsesBefore());
        assertThat(liveEdges(b)).isEqualTo(liveEdges(a));
        assertThat(rb.accuracyAfter()).isEqualTo(ra.accuracyAfter());
        assertThat(rb.synapsesAfter()).isEqualTo(ra.synapsesAfter());
    }

    @Test
    @DisplayName("veto edges survive an anneal at the most aggressive schedule")
    void vetoEdgesSurviveAnnealing() {
        Connectome c = annealGraph();
        c.registerVeto(NodeId.of("loadBearing"));
        SleepConsolidator.Result r = anneal(c, 99L, 50.0, 50.0, 40, 0);

        assertThat(connected(c, "src", "loadBearing"))
                .as("a veto edge must survive the most aggressive anneal")
                .isTrue();
        assertThat(liveEdges(c))
                .as("the veto edge itself must still be present in the graph")
                .contains("src->loadBearing");
        assertThat(r.accuracyAfter()).isGreaterThanOrEqualTo(r.accuracyBefore());
    }

    @Test
    @DisplayName("annealing actually shrinks the graph: prune runs exactly once at the end")
    void annealPrunesAtTheEnd() {
        Connectome c = annealGraph();
        SleepConsolidator.Result r = anneal(c, 3L, 0.2, 0.01, 16, 4);

        System.out.printf("  prune-at-end: live=%d removed=%d acc %.3f live=%s%n",
                c.liveSynapseCount(), r.removed(), r.accuracyAfter(), liveEdges(c));

        assertThat(c.liveSynapseCount())
                .as("the surviving edges must actually be REMOVED, not merely flagged")
                .isLessThan(r.synapsesBefore());
        assertThat(connected(c, "src", "loadBearing"))
                .as("a cold chain on this landscape must keep the load-bearing edge")
                .isTrue();
    }

    @Test
    @DisplayName("rejects an illegal schedule")
    void rejectsIllegalSchedule() {
        Connectome c = annealGraph();
        SleepConsolidator s =
                new SleepConsolidator(c, SleepConsolidatorAnnealTest::annealAccuracy, 1L);

        assertThatThrownBy(() -> s.annealConsolidate(0.0, 1.0, 0.1, 4, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.annealConsolidate(1.5, 1.0, 0.1, 4, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.annealConsolidate(0.5, 0.0, 0.1, 4, 1))
                .as("a zero start temperature would divide by zero in the Metropolis step")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.annealConsolidate(0.5, 1.0, 0.0, 4, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.annealConsolidate(0.5, 1.0, 0.1, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Measured, not assumed: sweeping T over {0.01, 0.2, 1.0, 5.0, 50.0} and seeds over
     * {1, 2, 3, 7, 42, 99} produced these held-out accuracies:
     *
     * <pre>
     * T=0.01  1.000 1.000 1.000 0.800 0.800 1.000
     * T=0.2   1.000 1.000 1.000 0.800 0.800 1.000
     * T=1.0   0.800 1.000 0.800 0.800 0.800 1.000
     * T=5.0   0.800 1.000 0.800 1.000 1.000 1.000
     * T=50.0  0.800 1.000 0.800 1.000 1.000 1.000
     * </pre>
     *
     * <p>So the chain is genuinely stochastic, and a hot chain lands in a measurably different
     * place from a cold one. These assertions pin that spread; if a mutation makes every seed
     * return the same state at every temperature, they fail.</p>
     */
    private static List<Double> accuracyPerSeed(double t) {
        List<Double> out = new ArrayList<>();
        for (long seed : new long[] {1L, 2L, 3L, 7L, 42L, 99L}) {
            out.add(anneal(annealGraph(), seed, t, t, 12, 3).accuracyAfter());
        }
        return out;
    }

    @Test
    @DisplayName("the chain is stochastic: different seeds land in different states")
    void theSeedIsObservable() {
        List<Double> hot = accuracyPerSeed(50.0);
        System.out.printf("  seed-observable T=50: %s%n", hot);

        assertThat(hot)
                .as("a sampler that ignored its RNG would return one value for every seed")
                .contains(0.8, 1.0);
        assertThat(hot.stream().distinct().count())
                .as("seeds must not all land in the same state")
                .isGreaterThan(1L);
    }

    @Test
    @DisplayName("temperature is load-bearing: hot and cold chains behave differently")
    void temperatureChangesWhereTheSearchLands() {
        List<Double> cold = accuracyPerSeed(0.01);
        List<Double> hot = accuracyPerSeed(50.0);
        System.out.printf("  temperature cold=%s hot=%s%n", cold, hot);

        assertThat(cold).isNotEqualTo(hot);
        assertThat(cold.get(0))
                .as("the cold chain is greedy: pruning junk always improves the score, so it "
                        + "commits to the first improvement and reaches the optimum here")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("the best-state guard holds across the whole measured temperature range")
    void neverWorseAcrossTheMeasuredRange() {
        for (double t : new double[] {0.01, 0.2, 1.0, 5.0, 50.0}) {
            for (long seed : new long[] {1L, 2L, 3L, 7L, 42L, 99L}) {
                Connectome c = annealGraph();
                SleepConsolidator.Result r = anneal(c, seed, t, t, 12, 3);
                assertThat(r.accuracyAfter())
                        .as("T=%s seed=%s must never return a state worse than the entry state", t, seed)
                        .isGreaterThanOrEqualTo(r.accuracyBefore());
                assertThat(connected(c, "src", "loadBearing"))
                        .as("T=%s seed=%s removed the load-bearing edge", t, seed)
                        .isTrue();
            }
        }
    }
}
