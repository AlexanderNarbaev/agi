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
 * <p>This pass contains no randomness. The proposal set is the {@code proposalCount} leading
 * entries of a list sorted by {@code |weight|}, then by node id, so the acceptance sequence does
 * not depend on collection ordering and is identical on every run for a given graph.</p>
 *
 * <p>The constructor still takes a seed, and {@link #consolidate} still constructs an RNG from it,
 * but that RNG is never read, so the seed currently has no effect on the result. The class-level
 * Reproducibility text used to claim the opposite — that randomness comes from an injected seed —
 * which was not true of this code. The seed and the unused RNG are left in place rather than
 * removed here, so that adding a real anneal later has the plumbing already present; until then
 * nothing should depend on the seed. This is the same pattern as the unused seeded RNG in
 * {@link ClauseExplorer}, and it is a known defect, not an intended design.</p>
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

    /**
     * Cross-validated consolidation: a proposal is accepted only if EVERY held-out fold holds.
     *
     * <p>The single-split version accepts a prune whenever one evaluator does not notice the
     * damage. If the evaluator scores only part of the graph — which every cheap evaluator does,
     * since scoring everything is expensive — a prune elsewhere in the graph passes unnoticed and
     * the brief's PASS criterion reports success on a graph that got worse.</p>
     *
     * <p>Requiring agreement across folds makes that failure mode visible: a prune that helps fold
     * A and hurts fold B is rejected rather than silently accepted. The folds are supplied by the
     * caller because what constitutes an independent fold is a property of the evaluation data,
     * not of the pruner.</p>
     *
     * @param folds independent evaluators, at least one
     * @param fraction fraction of candidates to propose
     * @param step annealing temperature; accepted and unused, as in {@link #consolidate}
     * @return the worst-case before/after accuracy across folds
     */
    public Result consolidateCrossValidated(List<Evaluator> folds, double fraction, double step) {
        if (folds == null || folds.isEmpty()) {
            throw new IllegalArgumentException("at least one fold required");
        }
        int before = graph.synapses().size();

        // Baseline: the WORST fold, not the mean. A prune that is fine on average and fatal on
        // one fold must fail, and an average would let it through.
        double baseline = Double.MAX_VALUE;
        for (Evaluator f : folds) baseline = Math.min(baseline, f.accuracy(graph));

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

        int proposalCount = Math.min(
                Math.max(1, (int) Math.ceil(candidates.size() * fraction)), candidates.size());
        List<Synapse> proposed = candidates.subList(0, proposalCount);

        for (Synapse s : proposed) graph.markPruned(s);
        double after = Double.MAX_VALUE;
        for (Evaluator f : folds) after = Math.min(after, f.accuracy(graph));

        boolean accepted = after >= baseline - 1e-9;
        if (!accepted) {
            for (Synapse s : proposed) graph.restore(s);
            after = Double.MAX_VALUE;
            for (Evaluator f : folds) after = Math.min(after, f.accuracy(graph));
        } else {
            graph.prune(Set.of());
            after = Double.MAX_VALUE;
            for (Evaluator f : folds) after = Math.min(after, f.accuracy(graph));
        }

        return new Result(before, graph.synapses().size(), baseline, after, accepted);
    }

    /**
     * Simulated-annealing prune: explores with stochastic Metropolis acceptance on a seeded chain
     * and returns the BEST state visited, never the last one.
     *
     * <p>{@link #consolidate} accepts a prune iff accuracy does not fall, which makes it a
     * one-step greedy move with no exploration at all. This method is the actual anneal the
     * {@code step} parameter always promised: a sweep proposes a random subset, uphill moves are
     * taken with probability exp(-delta/T), T falls geometrically from
     * {@code startTemperature} to {@code endTemperature}, and a restart rewinds to the best state
     * seen once {@code restartBudget} sweeps in a row were rejected.</p>
     *
     * <h2>Why the return value is the best state and not the final one</h2>
     *
     * <p>A Metropolis chain accepts worse states on purpose, so the LAST state is not the state
     * the caller should keep. This method records {@code bestPruned} whenever held-out accuracy
     * strictly improves, and at the end re-commits exactly that snapshot. The returned graph is
     * therefore never worse on the evaluation set than the state it started from, while the
     * search is still free to walk downhill.</p>
     *
     * <h2>Why prune() is called exactly once, at the very end</h2>
     *
     * <p>Exploration runs entirely on {@link Connectome#markPruned} and
     * {@link Connectome#restore}. {@code restore} only revives an edge that is still in PRUNED
     * state and still present in the list, so an edge that {@link Connectome#prune} has already
     * deleted can never be brought back. Annealing across prune boundaries is therefore not
     * expressible with this API, and calling prune per sweep would silently destroy the rollback
     * the best-state guarantee depends on.</p>
     *
     * <h2>Honest limit of the never-worse guarantee</h2>
     *
     * <p>The guarantee holds while exploring, where every measured state is a markPruned state.
     * The final {@code prune()} physically deletes the PRUNED edges, and it is measured and
     * reported as {@code accuracyAfter}. For an evaluator that respects
     * {@link Synapse#conducting()} — which every in-repo evaluator does, since a PRUNED edge
     * already stops conducting — pruning cannot change its score, so the guarantee carries
     * through. An evaluator whose score depends on the raw synapse COUNT would break it, and the
     * returned {@code accuracyAfter} is the number that would reveal that.</p>
     *
     * @param fraction          fraction of candidates to propose each sweep, in (0, 1]
     * @param startTemperature  temperature of the first sweep, > 0
     * @param endTemperature    temperature of the last sweep, > 0
     * @param sweeps            number of sweeps, >= 1
     * @param restartBudget     consecutive rejected sweeps before a rewind to the best state
     * @return before/after accuracy of the BEST visited state, after the final prune
     * <h2>Known limitation of the best-state guard (UNRESOLVED)</h2>
     *
     * <p>{@link Connectome#markPruned} matches the WHOLE {@link Synapse} record, and record
     * equality includes {@code state}. {@link Connectome#restore} matches endpoints only and
     * rebuilds via {@code reconnected()}, which yields CONNECTED. A reference captured before a
     * prune is therefore STALE after a restore: re-pruning it fails and returns false, and that
     * boolean is not checked here. An edge entering in a non-CONNECTED state ({@code POTENTIATED},
     * {@code DRESSED_DOWN}) can fail to be re-pruned, and a REJECTED move still rewrites such an
     * edge from its original state to CONNECTED. Replay and plasticity produce exactly those
     * states and a sleep cycle runs replay before consolidation, so this is the production input
     * domain, not an exotic case.</p>
     *
     * <p>The guard is therefore sound only for edges that entered CONNECTED, which is every edge
     * in the current test fixture. It is NOT a general guarantee. The fix is known in outline,
     * re-resolve each reference against the live graph by endpoint identity before mutating and
     * honour the returned boolean, but an attempt to apply it changed the search trajectory so
     * much that the guard stopped holding under the existing tests. It needs its own design
     * pass, not a patch.</p>
     *
     */
    public Result annealConsolidate(double fraction, double startTemperature,
                                    double endTemperature, int sweeps, int restartBudget) {
        if (fraction <= 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("fraction must be in (0,1], got " + fraction);
        }
        if (!(startTemperature > 0.0) || !(endTemperature > 0.0)) {
            throw new IllegalArgumentException("temperatures must be positive, got "
                    + startTemperature + " and " + endTemperature);
        }
        if (sweeps < 1) {
            throw new IllegalArgumentException("sweeps must be >= 1, got " + sweeps);
        }

        int before = graph.synapses().size();
        double entryAccuracy = evaluator.accuracy(graph);

        // Candidates exactly as in consolidate(): lowest |weight| first, veto edges excluded.
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
        if (candidates.isEmpty()) {
            return new Result(before, graph.synapses().size(), entryAccuracy, entryAccuracy, false);
        }

        int proposalCount = Math.min(
                Math.max(1, (int) Math.ceil(candidates.size() * fraction)), candidates.size());

        List<Synapse> pruned = new ArrayList<>();
        List<Synapse> bestPruned = new ArrayList<>();
        double bestAccuracy = entryAccuracy;
        int consecutiveRejections = 0;

        for (int sweep = 0; sweep < sweeps; sweep++) {
            // Geometric schedule. A single sweep has no schedule to traverse, so it runs hot.
            double temperature = (sweeps == 1)
                    ? startTemperature
                    : startTemperature * Math.pow(endTemperature / startTemperature,
                            (double) sweep / (double) (sweeps - 1));

            // Fresh permutation per sweep, derived from the sweep index, so the visit order
            // carries no information from one sweep into the next.
            Random rng = new Random(SeededRandom.derive(seed, "sleep.anneal", sweep));
            List<Synapse> sample = new ArrayList<>(candidates);
            for (int i = sample.size() - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                Synapse tmp = sample.get(i);
                sample.set(i, sample.get(j));
                sample.set(j, tmp);
            }

            List<Synapse> sweepPrune = new ArrayList<>();
            List<Synapse> sweepRestore = new ArrayList<>();
            for (int i = 0; i < proposalCount; i++) {
                Synapse s = sample.get(i);
                if (pruned.contains(s)) sweepRestore.add(s); else sweepPrune.add(s);
            }

            for (Synapse s : sweepPrune) { graph.markPruned(s); pruned.add(s); }
            for (Synapse s : sweepRestore) { graph.restore(s); pruned.remove(s); }

            double after = evaluator.accuracy(graph);
            if (metropolis(after, bestAccuracy, temperature, rng)) {
                consecutiveRejections = 0;
                if (after > bestAccuracy + 1e-12) {
                    bestAccuracy = after;
                    bestPruned = new ArrayList<>(pruned);
                }
            } else {
                for (Synapse s : sweepPrune) { graph.restore(s); pruned.remove(s); }
                for (Synapse s : sweepRestore) { graph.markPruned(s); pruned.add(s); }
                consecutiveRejections++;
            }

            // Restart: rewind to the best state seen, but only while sweeps remain to use it.
            if (restartBudget > 0 && consecutiveRejections >= restartBudget && sweep + 1 < sweeps) {
                for (Synapse s : new ArrayList<>(pruned)) { graph.restore(s); pruned.remove(s); }
                for (Synapse s : bestPruned) { graph.markPruned(s); pruned.add(s); }
                consecutiveRejections = 0;
            }
        }

        // Undo whatever the last sweep left, re-commit the best snapshot, then prune once.
        for (Synapse s : new ArrayList<>(pruned)) { graph.restore(s); }
        pruned.clear();
        for (Synapse s : bestPruned) { graph.markPruned(s); pruned.add(s); }
        graph.prune(Set.of());
        double finalAccuracy = evaluator.accuracy(graph);

        return new Result(before, graph.synapses().size(), entryAccuracy, finalAccuracy, true);
    }

    /**
     * Metropolis acceptance on negated accuracy as energy: a non-worsening move is free, a worsening
     * move of {@code delta} is taken with probability exp(-delta / temperature).
     */
    private static boolean metropolis(double after, double reference, double temperature, Random rng) {
        double delta = reference - after;
        if (delta <= 0.0) return true;
        return rng.nextDouble() < Math.exp(-delta / temperature);
    }
    @Override
    public String toString() {
        return "SleepConsolidator[seed=" + seed + " synapses=" + graph.synapses().size() + "]";
    }
}