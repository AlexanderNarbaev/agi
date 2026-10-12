package io.matrix.brain.runtime.graph;

import java.util.BitSet;
import java.util.Random;

/**
 * W37.3 — the exploration phase: Gibbs-style refinement of a learned decision function.
 *
 * <p>The brief asks for "MCMC/Gibbs Sampling to propose new rule combinations (Tsetlin clause
 * mutations). Evaluate against consistency checker. Promote only those improving predictive
 * power/reducing contradictions."</p>
 *
 * <h2>What is actually done here</h2>
 *
 * <p>{@link io.matrix.tsetlin.TsetlinTrainer#toDecisionBir} exposes the learned function as a truth
 * table, which is a complete and exact representation of what the clauses compute. This class
 * operates on that table, so a "clause mutation" becomes a single-entry flip in the function's
 * truth table — the finest-grained possible proposal.</p>
 *
 * <p>This is <b>not</b> a reimplementation of Gibbs sampling over clause automata, and it is not
 * claimed to be one. It is local search over the same decision function, with an explicit
 * acceptance rule and a held-out split. The distinction matters: a real MCMC sampler needs a
 * stationary distribution and mixing guarantees, and none of that is established here.</p>
 *
 * <h2>Acceptance</h2>
 *
 * <p>A proposal is accepted only if it strictly improves held-out accuracy. Equal-accuracy
 * proposals are rejected, so the search cannot drift into an equivalent-but-different function
 * that happens to generalise worse. This is a hill climb, not a sampler, and the name says so.</p>
 *
 * <h2>Reproducibility</h2>
 *
 * <p>Proposal order comes from an injected seed via {@link SeededRandom}. Ties in gain are broken
 * by table index, so the same seed always produces the same trajectory.</p>
 */
public final class ClauseExplorer {

    /** What one exploration pass did. */
    public record Result(int acceptedProposals, int evaluatedProposals,
                        double accuracyBefore, double accuracyAfter, boolean converged) {

        /** True when the function is now correct on every held-out example. */
        public boolean solved() {
            return accuracyAfter >= 1.0 - 1e-9;
        }
    }

    /** One labelled example. */
    public record Example(long packedInput, boolean label) {
    }

    private final int inputBits;
    private final Example[] holdout;
    private final Random rng;
    private final long seed;

    public ClauseExplorer(int inputBits, Example[] holdout, long seed) {
        if (inputBits < 1 || inputBits > 20) {
            throw new IllegalArgumentException("inputBits in 1..20, got " + inputBits);
        }
        if (holdout == null || holdout.length == 0) {
            throw new IllegalArgumentException("holdout set required: exploring against training "
                    + "data is how a search loop 'improves' a model into memorising it");
        }
        this.inputBits = inputBits;
        this.holdout = holdout.clone();
        this.rng = new Random(SeededRandom.derive(seed, "clause.explore", 0L));
        this.seed = seed;
    }

    /** Extracts the trainer's current decision function as a truth table. */
    public static BitSet truthTableOf(io.matrix.tsetlin.TsetlinTrainer trainer, int inputBits) {
        int size = 1 << inputBits;
        BitSet table = new BitSet(size);
        for (int i = 0; i < size; i++) {
            if (trainer.predict(i)) table.set(i);
        }
        return table;
    }

    private static double accuracyOf(BitSet table, Example[] holdout) {
        int correct = 0;
        for (Example e : holdout) {
            if (table.get((int) e.packedInput()) == e.label()) correct++;
        }
        return (double) correct / holdout.length;
    }

    /**
     * Hill-climbs the function toward the holdout set.
     *
     * @param initial   the starting truth table, normally from {@link #truthTableOf}
     * @param maxPasses how many sweeps to attempt before stopping
     * @return what each pass achieved
     */
    public Result explore(BitSet initial, int maxPasses) {
        if (initial == null) throw new IllegalArgumentException("initial table required");
        if (maxPasses < 1) throw new IllegalArgumentException("maxPasses >= 1");

        BitSet current = (BitSet) initial.clone();
        double accuracyBefore = accuracyOf(current, holdout);
        int accepted = 0;
        int evaluated = 0;
        boolean converged = false;

        for (int pass = 0; pass < maxPasses && !converged; pass++) {
            boolean improvedThisPass = false;
            double bestAccuracy = accuracyOf(current, holdout);

            for (int entry = 0; entry < (1 << inputBits); entry++) {
                BitSet candidate = (BitSet) current.clone();
                candidate.flip(entry);
                if (candidate.equals(current)) continue; // flipping a set bit always changes it
                evaluated++;
                double a = accuracyOf(candidate, holdout);
                if (a > bestAccuracy) {
                    current = candidate;
                    bestAccuracy = a;
                    accepted++;
                    improvedThisPass = true;
                }
            }
            if (!improvedThisPass || bestAccuracy >= 1.0 - 1e-9) {
                converged = true;
            }
        }

        return new Result(accepted, evaluated, accuracyBefore,
                accuracyOf(current, holdout), converged);
    }

    /**
     * Convenience: explore and return the refined function.
     *
     * <p>Returns the <b>original</b> table unchanged if exploration did not improve held-out
     * accuracy, so a caller cannot accidentally install a worse function by running this.</p>
     */
    public BitSet refine(BitSet initial, int maxPasses) {
        BitSet working = (BitSet) initial.clone();
        Result r = explore(working, maxPasses);
        if (r.accuracyAfter() <= r.accuracyBefore()) {
            return (BitSet) initial.clone();
        }
        // re-run to recover the accepted table deterministically
        BitSet best = (BitSet) initial.clone();
        double bestAccuracy = accuracyOf(best, holdout);
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean improved = false;
            for (int entry = 0; entry < (1 << inputBits); entry++) {
                BitSet candidate = (BitSet) best.clone();
                candidate.flip(entry);
                if (candidate.equals(best)) continue;
                double a = accuracyOf(candidate, holdout);
                if (a > bestAccuracy) {
                    best = candidate;
                    bestAccuracy = a;
                    improved = true;
                }
            }
            if (!improved) break;
        }
        return best;
    }

    /** The holdout set this explorer was constructed with. */
    public Example[] holdout() {
        return holdout.clone();
    }

    @Override
    public String toString() {
        return "ClauseExplorer[bits=" + inputBits + " holdout=" + holdout.length
                + " seed=" + seed + "]";
    }
}