package io.matrix.brain.runtime.graph;

import java.util.BitSet;
import java.util.Random;

/**
 * S5.1 (RECON-W38.3) — a seeded Gibbs sampler over the decision function's truth table.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@link ClauseExplorer} is a hill climb: it proposes single-entry flips and accepts only
 * strictly better ones. That cannot leave a local optimum, because every escape route must pass
 * through a state that is worse. The research record
 * ({@code docs-v2/research/RECON-W37-MCMC-SAMPLING.md}) established that no MCMC sampler existed in
 * the shipped training path and that calling the existing hill climb "Gibbs sampling" would be
 * false attribution. This class is the real thing, and it is separate from the hill climb rather
 * than a replacement for it.</p>
 *
 * <h2>What is actually being sampled</h2>
 *
 * <p>The target is the same representation {@link ClauseExplorer} uses: the learned function's
 * truth table, which {@code TsetlinTrainer#toDecisionBir} exposes exactly. Each truth-table entry is
 * a latent variable taking values in {0,1}. The energy is the number of held-out examples the
 * table misclassifies (0/1 loss):</p>
 *
 * <pre>E(x) = |{ e : x[e.input] != e.label }|</pre>
 *
 * <p>Because the loss depends on entry {@code i} only through examples whose input is {@code i},
 * the Gibbs full conditionals are available in closed form. Writing {@code m0} for the examples at
 * input {@code i} labelled true and {@code m1} for those labelled false:</p>
 *
 * <pre>E(x_i = 1) = m1        E(x_i = 0) = m0
 * P(x_i = 1 | x_-i) = 1 / (1 + exp((m1 - m0) / T))</pre>
 *
 * <p>One sweep resamples every entry exactly once, in a seeded random order (a Gibbs sweep requires
 * that the visit order carry no information from the current state).</p>
 *
 * <h2>Temperature</h2>
 *
 * <p>{@code T} is the annealing knob and the caller supplies both endpoints; this class only
 * interpolates geometrically between them, one temperature per sweep. At high temperature the
 * chain can accept an uphill move and cross a local optimum; at low temperature it concentrates on
 * the minimum-energy table.</p>
 *
 * <h2>What this class does NOT claim</h2>
 *
 * <ul>
 *   <li>This is <b>not</b> a posterior over Tsetlin clause automata. It samples truth-table
 *       entries, a strictly coarser and different representation.</li>
 *   <li><b>No mixing guarantee is established.</b> Nothing here proves the chain converges to the
 *       global optimum, and {@link Result#converged()} means only "no entry changed during a
 *       sweep", never "this is the global optimum".</li>
 *   <li>With a fully observed deterministic table and a 0/1 loss the energy minimum is attained
 *       wherever the holdout is satisfiable, so this sampler is a way of escaping local optima
 *       that the strict hill climb is structurally unable to leave. It is not a source of
 *       calibrated uncertainty, and nothing here measures retrieval quality.</li>
 *   <li>Parity against the hill-climb baseline is a separate, measured claim (S5.3) and is not
 *       established by this class's own tests.</li>
 * </ul>
 *
 * <h2>Reproducibility and wiring status</h2>
 *
 * <p>The sweep order and every resampling decision come from an injected seed via
 * {@link SeededRandom#derive}, never from a shared generator. The same seed reproduces the same
 * trajectory exactly. This class has <b>no production caller</b>; like {@link ClauseExplorer} it is
 * reachable only from its own tests, and nothing about it is on a shipped decision path.</p>
 */
public final class ClauseGibbsSampler {

    /**
     * Outcome of a run of {@link #sample}.
     *
     * @param converged true when the last sweep changed nothing. This is chain stasis, NOT proof
     *                  of optimality: a table already sitting at a local minimum is also
     *                  "converged", and nothing here establishes that the sampled table is the
     *                  global energy minimum.
     * @param table     the sampled truth table. Defensively copied on construction, because the
     *                  chain mutates its working copy in place and a caller must not be handed a
     *                  live reference into it. The argument passed to {@code sample} is never
     *                  modified, which is why the sampled table has to come back this way.
     */
    public record Result(int sweepsRun, int entriesResampled, int entriesChanged,
                         double accuracyBefore, double accuracyAfter, boolean converged,
                         BitSet table) {

        public Result {
            table = (BitSet) table.clone();
        }

        @Override
        public BitSet table() {
            return (BitSet) table.clone();
        }
    }

    private final int inputBits;
    private final ClauseExplorer.Example[] holdout;

    /** mislabels[i] = examples at input i labelled TRUE; mislabelsFalse[i] = labelled FALSE. */
    private final int[] trueAt;
    private final int[] falseAt;

    private final long seed;

    /**
     * @param inputBits width of the decision function, 1..20
     * @param holdout   labelled examples the sampler is allowed to score against
     * @param seed      root seed; the sweep order derives from it deterministically
     */
    public ClauseGibbsSampler(int inputBits, ClauseExplorer.Example[] holdout, long seed) {
        if (inputBits < 1 || inputBits > 20) {
            throw new IllegalArgumentException("inputBits in 1..20, got " + inputBits);
        }
        if (holdout == null || holdout.length == 0) {
            throw new IllegalArgumentException("holdout set required: a Gibbs sampler scored "
                    + "against its own training data cannot be evaluated");
        }
        this.inputBits = inputBits;
        this.holdout = holdout.clone();
        this.seed = seed;
        int size = 1 << inputBits;
        this.trueAt = new int[size];
        this.falseAt = new int[size];
        for (ClauseExplorer.Example e : this.holdout) {
            int idx = (int) e.packedInput();
            if (idx < 0 || idx >= size) {
                throw new IllegalArgumentException("example input " + idx
                        + " outside the table for inputBits=" + inputBits);
            }
            if (e.label()) {
                trueAt[idx]++;
            } else {
                falseAt[idx]++;
            }
        }
    }

    /** Held-out accuracy of a table, for before/after reporting. */
    public double accuracyOf(BitSet table) {
        int correct = 0;
        for (ClauseExplorer.Example e : holdout) {
            if (table.get((int) e.packedInput()) == e.label()) correct++;
        }
        return (double) correct / holdout.length;
    }

    /**
     * Probability that entry {@code i} takes value true, from its Gibbs full conditional.
     *
     * <p>Evaluated in a numerically stable form: the naive {@code 1/(1+exp(d))} overflows for
     * large positive {@code d}, and an overflow here would silently return 0.0 and make the chain
     * deterministic.</p>
     */
    static double probabilityTrue(int trueAtEntry, int falseAtEntry, double temperature) {
        double d = ((double) falseAtEntry - (double) trueAtEntry) / temperature;
        if (d >= 0.0) {
            double e = Math.exp(-d);
            return e / (1.0 + e);
        }
        double e = Math.exp(d);
        return 1.0 / (1.0 + e);
    }

    /**
     * Runs Gibbs sweeps with a geometric temperature schedule.
     *
     * @param initial          starting truth table
     * @param sweeps           number of sweeps, at least 1
     * @param startTemperature temperature of the first sweep, > 0
     * @param endTemperature   temperature of the final sweep, > 0 and at most startTemperature
     * @return what the chain did
     */
    public Result sample(BitSet initial, int sweeps,
                         double startTemperature, double endTemperature) {
        if (initial == null) throw new IllegalArgumentException("initial table required");
        if (sweeps < 1) throw new IllegalArgumentException("sweeps >= 1, got " + sweeps);
        if (!(startTemperature > 0.0)) {
            throw new IllegalArgumentException("startTemperature > 0, got " + startTemperature);
        }
        if (!(endTemperature > 0.0)) {
            throw new IllegalArgumentException("endTemperature > 0, got " + endTemperature);
        }
        if (endTemperature > startTemperature) {
            throw new IllegalArgumentException("cooling must not raise the temperature: "
                    + endTemperature + " > " + startTemperature);
        }

        BitSet current = (BitSet) initial.clone();
        double accuracyBefore = accuracyOf(current);

        int size = 1 << inputBits;
        double logRatio = Math.log(endTemperature / startTemperature);
        int resampled = 0;
        int changed = 0;
        boolean stasis = false;

        for (int sweep = 0; sweep < sweeps; sweep++) {
            double temperature = sweeps == 1
                    ? endTemperature
                    : startTemperature * Math.exp(logRatio * ((double) sweep / (sweeps - 1)));

            // A fresh permutation per sweep: the visit order must not carry information about
            // the current state, otherwise this is not a Gibbs sweep.
            int[] order = new int[size];
            for (int i = 0; i < size; i++) order[i] = i;
            Random sweepRng =
                    new Random(SeededRandom.derive(seed, "clause.gibbs", sweep));
            for (int i = size - 1; i > 0; i--) {
                int j = sweepRng.nextInt(i + 1);
                int tmp = order[i];
                order[i] = order[j];
                order[j] = tmp;
            }

            int changedThisSweep = 0;
            for (int entry : order) {
                double p = probabilityTrue(trueAt[entry], falseAt[entry], temperature);
                boolean next = sweepRng.nextDouble() < p;
                resampled++;
                if (next != current.get(entry)) {
                    current.set(entry, next);
                    changed++;
                    changedThisSweep++;
                }
            }
            if (changedThisSweep == 0) {
                stasis = true;
                break;
            }
        }

        return new Result(sweeps, resampled, changed, accuracyBefore,
                accuracyOf(current), stasis, current);
    }

    /** The seed this sampler was constructed with, for replay records. */
    public long seed() {
        return seed;
    }

    /** Width of the decision function this sampler explores. */
    public int inputBits() {
        return inputBits;
    }
}
