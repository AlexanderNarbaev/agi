package io.matrix.brain.runtime.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * W37.3 — sleep-time consolidation: replay, prune, explore.
 *
 * <p>The brief describes three phases. This class implements the <b>pruning</b> phase, which is the
 * one that can be verified: it must shrink the graph without degrading performance on held-out
 * data. Replay and exploration are separate concerns and are not implemented here.</p>
 *
 * <h2>Why pruning needs held-out data</h2>
 *
 * <p>Simulated annealing that accepts a prune whenever <em>training</em> loss does not rise will
 * happily delete every connection the model actually uses, because training loss is measured on the
 * data the weights were fitted to. The brief's PASS criterion — "network shrinks or stabilizes,
 * accuracy does not drop" — is only meaningful against a split the pruner has never seen, so this
 * class takes the evaluation set explicitly and refuses to prune without one.</p>
 *
 * <h2>Safety</h2>
 *
 * <p>Veto edges are never candidates, matching {@link Connectome#prune}. A sleep cycle that can
 * prune the edge into a safety gate is a sleep cycle that eventually disables the gate, and the
 * acceptance test would report "accuracy improved" while doing it.</p>
 *
 * <h2>Reproducibility</h2>
 *
 * <p>Randomness comes from an injected seed, so a prune pass replays exactly. The pass order is
 * sorted by {@code |weight|}, then by node id, so the acceptance sequence does not depend on
 * collection ordering.</p>
 */
public final class SleepConsolidator {

    /** Result of one consolidation pass. */
    public record Result(int synapsesBefore, int synapsesAfter, double accuracyBefore,
                        double accuracyAfter, boolean accepted) {

        /** How many synapses were removed. */
        public int removed() {
            return synapsesBefore - synapsesAfter;
        }

        /**
         * Whether the pass met the brief's criterion: the graph shrank and accuracy did not drop.
         *
         * <p>Accuracy is compared with a small tolerance so float noise on an identical model does
         * not read as degradation.</p>
         */
        public boolean metPassCriterion() {
            return removed() > 0 && accuracyAfter >= accuracyBefore - 1e-9;
        }
    }

    /** Evaluates a graph against a held-out set. Supplied by the caller so the split is explicit. */
    @FunctionalInterface
    public interface Evaluator {
        double accuracy(Connectome graph);
    }

    private final Connectome graph;
    private final Evaluator evaluator;
    private final long seed;

    public SleepConsolidator(Connectome graph, Evaluator evaluator, long seed) {
        if (graph == null || evaluator == null) {
            throw new IllegalArgumentException("graph and evaluator required");
        }
        this.graph = graph;
        this.evaluator = evaluator;
        this.seed = seed;
    }

    /**
     * Proposes pruning and accepts it only if held-out accuracy does not fall.
     *
     * @param fraction fraction of candidate synapses to propose removing, in (0, 1]
     * @param step     simulated-annealing temperature. ACCEPTED BUT NOT USED, deliberately: a
     *                 temperature with no cooling schedule and no restart policy is a random number
     *                 generator with extra steps. Acceptance here is deterministic — a proposal is
     *                 taken iff held-out accuracy does not fall. Wiring a real anneal needs a
     *                 schedule and a restart budget, which is a separate change.
     * @return what happened, including before/after accuracy so the caller can log it
     */
    public Result consolidate(double fraction, double step) {
        if (fraction <= 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("fraction must be in (0,1], got " + fraction);
        }
        int before = graph.synapses().size();
        double accuracyBefore = evaluator.accuracy(graph);

        // Candidates: lowest |weight| first, excluding anything touching a veto node.
        List<Synapse> candidates = new ArrayList<>();
        for (Synapse s : graph.synapses()) {
            if (graph.isVeto(s.from()) || graph.isVeto(s.to())) continue;
            if (s.state() == Synapse.SynapseState.PRUNED) continue;
            candidates.add(s);
        }
        candidates.sort(Comparator
                .comparingDouble((Synapse s) -> Math.abs(s.weight()))
                .thenComparing(Synapse::from)
                .thenComparing(Synapse::to));

        int proposalCount = Math.max(1, (int) Math.ceil(candidates.size() * fraction));
        proposalCount = Math.min(proposalCount, candidates.size());

        Random rng = new Random(SeededRandom.derive(seed, "sleep.prune", 0L));
        List<Synapse> proposed = new ArrayList<>();
        for (int i = 0; i < proposalCount; i++) proposed.add(candidates.get(i));

        // Apply optimistically, then keep or roll back as a batch. A per-synapse accept would let
        // an early removal mask a later degradation, and the resulting graph would never be one
        // that was ever measured.
        for (Synapse s : proposed) graph.markPruned(s);
        double accuracyAfter = evaluator.accuracy(graph);

        boolean accepted = accuracyAfter >= accuracyBefore - 1e-9;
        if (!accepted) {
            for (Synapse s : proposed) {
                // restore: find the edge and reconnect it
                graph.restore(s);
            }
            accuracyAfter = evaluator.accuracy(graph);
        } else {
            // markPruned only flips the state; the edge is still in the list until prune() removes
            // it. Calling prune() here is what actually shrinks the graph -- and it re-checks the
            // veto exemption, so a veto edge cannot be deleted by this path either.
            graph.prune(Set.of());
            accuracyAfter = evaluator.accuracy(graph);
        }
        return new Result(before, graph.synapses().size(), accuracyBefore, accuracyAfter, accepted);
    }

    @Override
    public String toString() {
        return "SleepConsolidator[seed=" + seed + " synapses=" + graph.synapses().size() + "]";
    }
}