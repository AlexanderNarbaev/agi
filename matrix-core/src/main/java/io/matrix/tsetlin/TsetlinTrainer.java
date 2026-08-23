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

    private static final double S = 8.0;

    private final int inputBits;
    private final int nStates;
    private final Random rng;

    /** Clauses: [i][0] = automata for x_j, [i][1] = automata for ¬x_j. */
    private final List<TsetlinAutomaton[][]> clauses = new ArrayList<>();
    /** Polarity per clause: +1 contributes +1 when firing, −1 contributes −1. */
    private final List<Integer> polarity = new ArrayList<>();

    public TsetlinTrainer(int inputBits, int nClauses, int nStates, Random rng) {
        if (inputBits < 1 || inputBits > 20) {
            throw new IllegalArgumentException("inputBits in 1..20");
        }
        if (nClauses < 2) throw new IllegalArgumentException("nClauses >= 2");
        this.inputBits = inputBits;
        this.nStates = nStates;
        this.rng = rng;
        long seed = rng.nextLong();
        for (int c = 0; c < nClauses; c++) {
            var pair = new TsetlinAutomaton[2][inputBits];
            for (int j = 0; j < inputBits; j++) {
                int off = (int) (((seed >>> 4) + 31L * j + 61L * c) % nStates);
                pair[0][j] = new TsetlinAutomaton(nStates, 1 + off);
                pair[1][j] = new TsetlinAutomaton(nStates, 1 + ((off + nStates / 2) % nStates));
            }
            clauses.add(pair);
            polarity.add(c % 2 == 0 ? +1 : -1);
        }
    }

    private static boolean bit(long x, int j) {
        return ((x >>> j) & 1L) == 1L;
    }

    private boolean fires(TsetlinAutomaton[][] cl, long x) {
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            if (cl[0][j].includes() && !v) return false;
            if (cl[1][j].includes() && v) return false;
        }
        return true;
    }

    private void typeOne(TsetlinAutomaton[][] cl, long x) {
        // Canonical Granmo Type Ia rows (fired on matching target):
        //   lit TRUE  & included → Reward w.p. (s-1)/s   (deepen)
        //   lit TRUE  & excluded → Penalty w.p. 1/s      (grow toward include)
        //   lit FALSE & included → Penalty w.p. 1/s      (push out)
        //   lit FALSE & excluded → Reward w.p. (s-1)/s   (deepen exclusion)
        double pR = (S - 1.0) / S, pP = 1.0 / S;
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            var xp = cl[0][j]; var xn = cl[1][j];
            if (v) {
                if ((xp.includes() && rng.nextDouble() < pR)
                        || (!xp.includes() && rng.nextDouble() < pP)) xp.penalty();
                if ((xn.includes() && rng.nextDouble() < pP)
                        || (!xn.includes() && rng.nextDouble() < pR)) xn.reward();
            } else {
                if ((xn.includes() && rng.nextDouble() < pR)
                        || (!xn.includes() && rng.nextDouble() < pP)) xn.penalty();
                if ((xp.includes() && rng.nextDouble() < pP)
                        || (!xp.includes() && rng.nextDouble() < pR)) xp.reward();
            }
        }
    }

    private void typeOneGrowth(TsetlinAutomaton[][] cl, long x) {
        // Type Ib: pull excluded-but-true literals toward inclusion (weak).
        double pP = 1.0 / S;
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            if (!cl[0][j].includes() && v && rng.nextDouble() < pP) cl[0][j].reward();
            if (!cl[1][j].includes() && !v && rng.nextDouble() < pP) cl[1][j].reward();
        }
    }

    private void typeTwo(TsetlinAutomaton[][] cl, long x) {
        // Minimal repair: one excluded contradicting literal flips the clause
        // off for this input without destroying its remaining coverage.
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            if (!cl[0][j].includes() && !v) { cl[0][j].includeNow(); return; }
            if (!cl[1][j].includes() && v) { cl[1][j].includeNow(); return; }
        }
    }

    /** Train on a single labeled example (packed bits, bit j = feature j). */
    public void trainStep(long[] inputWords, boolean isPositive) {
        long x = pack(inputWords);
        for (int i = 0; i < clauses.size(); i++) {
            var cl = clauses.get(i);
            boolean target = isPositive == (polarity.get(i) == +1);
            boolean f = fires(cl, x);
            if (f) {
                if (target) typeOne(cl, x);
                else typeTwo(cl, x);
            } else if (target) {
                typeOneGrowth(cl, x);
            }
        }
    }

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

    public int clauseCount() { return clauses.size(); }

    public int inputBits() { return inputBits; }

    public int nStates() { return nStates; }
}
