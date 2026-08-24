package io.matrix.tsetlin;

import io.matrix.bir.Bir;
import io.matrix.bir.BirCompiler;
import io.matrix.bir.ClauseSetForm;
import io.matrix.bir.TtForm;
import io.matrix.bir.TruthTableAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TsetlinTrainer — primary BIR producer (SPEC-002 Stage B, FR-B1/B2).
 *
 * <p>Canonical Granmo Tsetlin machine for binary classification: {@code T}
 * clauses with alternating polarity {@code ±1}. Effective output of a clause
 * is {@code fires ? polarity : 0}; the class-1 score is the summed vote and
 * prediction is {@code score > 0}.
 *
 * <p>Training per example {@code (x,y)}: each clause has its own target
 * {@code t = (polarity == +1) ? y : !y}. Fired-and-matching ⇒ Type I
 * (true literals reinforce w.p. {@code (s-1)/s}, false literals pushed out
 * w.p. {@code 1/s}); fired-against ⇒ Type II minimal repair (one excluded
 * contradicting literal jumps into inclusion); non-firing-with-target ⇒
 * Type Ib growth pressure (excluded-true literals pulled in w.p. {@code 1/s}).
 *
 * <p>Export: the team decision function is distilled EXACTLY into BIR by
 * evaluating {@link #predict(long)} over the full space ({@code k ≤ 20}) and
 * compiling through {@code BirCompiler.ttToClauseSet}. Learning is stochastic,
 * seeded and lives outside the runtime contour (CONSTITUTION II.2–3).
 */
public final class TsetlinTrainer {

    private static final double S = 4.0;

    private final int inputBits;
    private final int nStates;
    private final Random rng;

    /** Clauses: [i][0] = automata for x_j, [i][1] = automata for ¬x_j. */
    private final List<TsetlinAutomaton[][]> clauses = new ArrayList<>();
    /** Polarity per clause: +1 contributes +1 when firing, −1 contributes −1. */
    private final List<Integer> polarity = new ArrayList<>();

    /** Clause-pair initialization strategy. */
    public enum InitStrategy {
        /** Reference-style uniform-random automaton states. */
        RANDOM,
        /** Deterministic complementary: x_j included, ¬x_j excluded. */
        COMPLEMENTARY
    }

    private final InitStrategy init;

    public TsetlinTrainer(int inputBits, int nClauses, int nStates, Random rng) {
        this(inputBits, nClauses, nStates, rng, InitStrategy.RANDOM);
    }

    public TsetlinTrainer(int inputBits, int nClauses, int nStates, Random rng, InitStrategy init) {
        if (inputBits < 1 || inputBits > 20) {
            throw new IllegalArgumentException("inputBits in 1..20");
        }
        if (nClauses < 2) throw new IllegalArgumentException("nClauses >= 2");
        this.inputBits = inputBits;
        this.nStates = nStates;
        this.rng = rng;
        this.init = init == null ? InitStrategy.RANDOM : init;
        long seed = rng.nextLong();
        for (int c = 0; c < nClauses; c++) {
            var pair = new TsetlinAutomaton[2][inputBits];
            for (int j = 0; j < inputBits; j++) {
                int off = (int) (((seed >>> 4) + 31L * j + 61L * c) % nStates);
                // Random init (reference-style): diverse starting subsets let
                // different clauses specialize onto different minterms; rare
                // dead (x∧¬x) clauses are absorbed by the pool.
                if (init == InitStrategy.RANDOM) {
                    pair[0][j] = new TsetlinAutomaton(nStates, 1 + rng.nextInt(2 * nStates));
                    pair[1][j] = new TsetlinAutomaton(nStates, 1 + rng.nextInt(2 * nStates));
                } else {
                    pair[0][j] = new TsetlinAutomaton(nStates, nStates + 1); // x_j in
                    pair[1][j] = new TsetlinAutomaton(nStates, 1);           // ¬x_j out
                }
            }
            clauses.add(pair);
            polarity.add(c % 2 == 0 ? +1 : -1);
        }
    }

    /** D3: canonical max_included_literals — clauses above the cap fall
     *  into the decay path instead of further reinforcement. */
    private int maxIncludedLiterals() {
        return Integer.MAX_VALUE; // canonical default: unlimited
    }

    private int countIncludes(TsetlinAutomaton[][] cl) {
        int n = 0;
        for (int j = 0; j < inputBits; j++) {
            if (cl[0][j].includes()) n++;
            if (cl[1][j].includes()) n++;
        }
        return n;
    }

    private static boolean bit(long x, int j) {
        return ((x >>> j) & 1L) == 1L;
    }

    private boolean fires(TsetlinAutomaton[][] cl, long x) {
        boolean anyIncluded = false;
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            if (cl[0][j].includes() && !v) return false;
            if (cl[1][j].includes() && v) return false;
            if (cl[0][j].includes() || cl[1][j].includes()) anyIncluded = true;
        }
        // Canonical D5: an all-exclude (empty) clause does NOT output 1.
        return anyIncluded;
    }

    private void typeOne(TsetlinAutomaton[][] cl, long x) {
        // Canonical Granmo Type Ia rows (fired on matching target):
        //   lit TRUE  & included → Reward w.p. (s-1)/s   (deepen include)
        //   lit TRUE  & excluded → Penalty w.p. 1/s      (grow toward include)
        //   lit FALSE & included → Penalty w.p. 1/s      (push out)
        //   lit FALSE & excluded → Reward w.p. (s-1)/s   (deepen exclusion)
        // Unified: consistency (literal-value == inclusion-state) ⇒ Reward,
        // mismatch ⇒ Penalty.
        double pR = (S - 1.0) / S, pP = 1.0 / S;
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            // D2 boost_true_positive_feedback=1 (canonical default):
            // consistency-reward is unconditional; mismatch-penalty keeps 1/s.
            boolean incX = cl[0][j].includes();
            if (incX == v) cl[0][j].reward();
            else { if (rng.nextDouble() < pP) cl[0][j].penalty(); }
            boolean incN = cl[1][j].includes();
            if ((!v) == incN) cl[1][j].reward();
            else { if (rng.nextDouble() < pP) cl[1][j].penalty(); }
        }
    }

    private void typeOneGrowth(TsetlinAutomaton[][] cl, long x) {
        // CANONICAL Type Ib (C ref: tm_dec over random feedback_to_la
        // subset): sampled automata take ONE STEP TOWARD EXCLUDE regardless
        // of current side — this gradually walks over-inclusive literals
        // OUT (prevents x∧¬x lock-in) while leaving correct exclusions to
        // random-walk within the exclude region.
        double pP = 1.0 / S;
        for (int j = 0; j < inputBits; j++) {
            if (rng.nextDouble() < pP) cl[0][j].penalty();
            if (rng.nextDouble() < pP) cl[1][j].penalty();
        }
    }

    /** Contradiction-safe inclusion: if the opposite literal is included,
     *  push IT out instead of creating an always-false x∧¬x pair. */
    private void includeSafe(TsetlinAutomaton[][] cl, int polIdx, int j) {
        var lit = cl[polIdx][j];
        var opp = cl[1 - polIdx][j];
        if (!lit.includes()) {
            if (opp.includes()) opp.penalty();
            else lit.includeNow();
        }
    }

    /** CANONICAL Type II (batch): EVERY excluded literal that would
     *  contradict the clause under this input jumps into inclusion at once —
     *  one-shot region pruning without progressive drain of included
     *  literals (progressive drain was our empty-collapse bug). */
    private void typeTwo(TsetlinAutomaton[][] cl, long x) {
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            if (!cl[0][j].includes() && !v) cl[0][j].includeNow();
            if (!cl[1][j].includes() && v) cl[1][j].includeNow();
        }
    }

    /**
     * Train on a single labeled example (packed bits, bit j = feature j).
     *
     * <p>Canonical soft gating (audit plan D1): each clause receives
     * feedback with probability {@code (T ± vote)/(2T)} — high while the
     * example-level vote disagrees with the clause's own target, decaying to
     * zero once the vote saturates in favour. Type II repair of against-
     * target firing clauses rides the same gate.
     */
    public void trainStep(long[] inputWords, boolean isPositive) {
        long x = pack(inputWords);
        int score = 0;
        boolean[] fired = new boolean[clauses.size()];
        for (int i = 0; i < clauses.size(); i++) {
            fired[i] = fires(clauses.get(i), x);
            if (fired[i]) score += polarity.get(i);
        }
        // D1' canonical PER-CLAUSE asymmetric gating (C ref ~331):
        // p_i = (T + (1 - 2*t_i)*class_sum) / (2T). Pos-target clauses fade
        // as vote → +T; opposite-target clauses get STRONGER pressure —
        // the self-balancing loop a flat |score| gate destroyed.
        for (int i = 0; i < clauses.size(); i++) {
            var cl = clauses.get(i);
            boolean target = isPositive == (polarity.get(i) == +1);
            int tBit = target ? 1 : 0;
            double p = ((double) FEEDBACK_T + (1 - 2 * tBit) * score) / (2.0 * FEEDBACK_T);
            p = Math.max(0.0, Math.min(1.0, p));
            if (rng.nextDouble() >= p) continue;
            if (fired[i]) {
                if (!target) typeTwo(cl, x);
                else if (countIncludes(cl) <= maxIncludedLiterals()) typeOne(cl, x);
                else typeOneGrowth(cl, x); // D3: over-inclusive → decay path
            } else if (target) {
                typeOneGrowth(cl, x);
            }
        }
    }

    /** Soft-gating saturation threshold for the example-level vote. */
    private static final int FEEDBACK_T = 12;

    /** Vote magnitude at which the example is considered decided. */
    private static final int FEEDBACK_MARGIN = 2;

    /** Train on a batch for the given number of epochs. */
    public void trainBatch(long[][] inputs, boolean[] labels, int epochs) {
        if (inputs.length != labels.length) {
            throw new IllegalArgumentException("inputs/labels length mismatch");
        }
        for (int e = 0; e < epochs; e++) {
            for (int i = 0; i < inputs.length; i++) trainStep(inputs[i], labels[i]);
        }
    }

    /** Class-1 iff the summed polarised vote over firing clauses is positive. */
    public boolean predict(long packedInput) {
        int score = 0;
        for (int i = 0; i < clauses.size(); i++) {
            if (fires(clauses.get(i), packedInput)) score += polarity.get(i);
        }
        return score > 0;
    }

    private static long pack(long[] words) {
        return words[0];
    }

    /**
     * Distills the team decision function into an exact CLAUSESET artifact
     * (espresso-type exact minimization via BirCompiler).
     */
    public ClauseSetForm toDecisionClauseSet(String provenance) {
        int size = 1 << inputBits;
        var bits = new java.util.BitSet(size);
        for (int i = 0; i < size; i++) bits.set(i, predict(i));
        var tt = io.matrix.neuron.TruthTable.of(inputBits, bits);
        return BirCompiler.ttToClauseSet(TruthTableAdapter.toBir(tt));
    }

    /** Same distillation exposed as a generic TT-form BIR artifact. */
    public Bir toDecisionBir(String provenance) {
        int size = 1 << inputBits;
        var bits = new java.util.BitSet(size);
        for (int i = 0; i < size; i++) bits.set(i, predict(i));
        var tt = io.matrix.neuron.TruthTable.of(inputBits, bits);
        return TruthTableAdapter.toBir(tt);
    }

    /** Legacy alias used by existing producers/tests. */
    public ClauseSetForm toClauseSet(String provenance) {
        return toDecisionClauseSet(provenance);
    }

    /** TEMP diagnostics */
    public String dbgClause(int i) {
        var cl = clauses.get(i);
        StringBuilder b = new StringBuilder("pol=" + polarity.get(i) + ": ");
        for (int j = 0; j < inputBits; j++)
            b.append(cl[0][j].includes() ? 'X' : '.').append(cl[1][j].includes() ? 'N' : '.').append(' ');
        return b.append("| fires@00..11=").append(fires(cl, 0)).append(fires(cl, 1))
                .append(fires(cl, 2)).append(fires(cl, 3)).toString();
    }

    public int clauseCount() { return clauses.size(); }

    public int inputBits() { return inputBits; }

    public int nStates() { return nStates; }
}
