package io.matrix.tsetlin;

import io.matrix.bir.Bir;
import io.matrix.bir.BirCompiler;
import io.matrix.bir.ClauseSetForm;
import io.matrix.bir.TruthTableAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * TsetlinTrainer — primary BIR producer (SPEC-002 Stage B, FR-B1/B2).
 *
 * <p>Canonical Granmo Tsetlin machine: clauses with alternating polarity ±1,
 * per-clause asymmetric soft feedback gating p=(T±vote)/2T (D1'), boost-
 * style unconditional consistency-reward (D2), TypeII batch includeNow of
 * contradicting excluded literals (D5-compatible), canonical pure-decay
 * TypeIb, optional max-included cap (D3). Decision function is distilled
 * EXACTLY into BIR via truth-table compilation. Learning is seeded-
 * stochastic and lives outside the runtime contour (CONSTITUTION II.2–3).
 */
public final class TsetlinTrainer {

    /** Default specificity when not overridden. */
    public static final double DEFAULT_S = 4.0;

    private final int inputBits;
    private final int nStates;
    private final Random rng;
    private final InitStrategy init;
    private final double sParam;
    private final int feedbackT;

    /** Clause = {pos[k] automata for x_j, neg[k] automata for ¬x_j}. */
    private final List<TsetlinAutomaton[][]> clauses = new ArrayList<>();
    private final List<Integer> polarity = new ArrayList<>();

    /** Clause-pair initialization strategy. */
    public enum InitStrategy {
        /** Reference-style uniform-random automaton states. */
        RANDOM,
        /** Deterministic complementary: x_j included, ¬x_j excluded. */
        COMPLEMENTARY
    }

    public TsetlinTrainer(int inputBits, int nClauses, int nStates, Random rng) {
        this(inputBits, nClauses, nStates, rng, InitStrategy.RANDOM, DEFAULT_S);
    }

    public TsetlinTrainer(int inputBits, int nClauses, int nStates, Random rng, InitStrategy init) {
        this(inputBits, nClauses, nStates, rng, init, DEFAULT_S);
    }

    public TsetlinTrainer(int inputBits, int nClauses, int nStates, Random rng,
                          InitStrategy init, double s) {
        if (inputBits < 1 || inputBits > 20) throw new IllegalArgumentException("inputBits in 1..20");
        if (nClauses < 2) throw new IllegalArgumentException("nClauses >= 2");
        if (s < 1.0) throw new IllegalArgumentException("s >= 1");
        this.inputBits = inputBits;
        this.nStates = nStates;
        this.rng = rng;
        this.init = init == null ? InitStrategy.RANDOM : init;
        this.sParam = s;
        this.feedbackT = Math.max(1, nClauses / 4); // vote saturation threshold
        long seed = rng.nextLong();
        for (int c = 0; c < nClauses; c++) {
            var pair = new TsetlinAutomaton[2][inputBits];
            for (int j = 0; j < inputBits; j++) {
                if (this.init == InitStrategy.RANDOM) {
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
        // Canonical D5: an all-exclude clause does NOT output 1.
        return anyIncluded;
    }

    private int countIncludes(TsetlinAutomaton[][] cl) {
        int n = 0;
        for (int j = 0; j < inputBits; j++) {
            if (cl[0][j].includes()) n++;
            if (cl[1][j].includes()) n++;
        }
        return n;
    }

    private int maxIncludedLiterals() {
        return Integer.MAX_VALUE; // D3 knob; canonical default unlimited
    }

    /**
     * D2-boosted canonical Type Ia on a fired own-target clause:
     * consistency (literal-value == inclusion-state) ⇒ unconditional Reward
     * into current side; mismatch ⇒ Penalty w.p. 1/s (step to other side).
     */
    private void typeOne(TsetlinAutomaton[][] cl, long x) {
        double pP = 1.0 / sParam;
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            boolean incX = cl[0][j].includes();
            if (incX == v) cl[0][j].reward();
            else if (rng.nextDouble() < pP) cl[0][j].penalty();
            boolean incN = cl[1][j].includes();
            if ((!v) == incN) cl[1][j].reward();
            else if (rng.nextDouble() < pP) cl[1][j].penalty();
        }
    }

    /**
     * CANONICAL Type II (fired against target): every excluded literal that
     * contradicts the clause under this input jumps into inclusion at once
     * (batch includeNow) — one-shot region pruning without progressive drain.
     */
    private void typeTwo(TsetlinAutomaton[][] cl, long x) {
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            if (!cl[0][j].includes() && !v) cl[0][j].includeNow();
            if (!cl[1][j].includes() && v) cl[1][j].includeNow();
        }
    }

    /**
     * CANONICAL Type Ib (non-fired, own-target): sampled automata take one
     * step toward exclusion regardless of side — gradually walks
     * over-inclusive literals out without speculative pull-in growth.
     */
    private void typeOneGrowth(TsetlinAutomaton[][] cl, long x) {
        double pP = 1.0 / sParam;
        for (int j = 0; j < inputBits; j++) {
            if (rng.nextDouble() < pP) cl[0][j].penalty();
            if (rng.nextDouble() < pP) cl[1][j].penalty();
        }
    }

    /**
     * Train on a single example with canonical PER-CLAUSE asymmetric soft
     * gating (D1'): p_i=(T±vote)/2T by own target; fired-and-matching ⇒
     * Type Ia (capped by max-included, else falls to Ib decay); fired-
     * against ⇒ Type II; non-fired own-target ⇒ Type Ib.
     */
    public void trainStep(long[] inputWords, boolean isPositive) {
        long x = pack(inputWords);
        int score = 0;
        boolean[] fired = new boolean[clauses.size()];
        for (int i = 0; i < clauses.size(); i++) {
            fired[i] = fires(clauses.get(i), x);
            if (fired[i]) score += polarity.get(i);
        }
        for (int i = 0; i < clauses.size(); i++) {
            var cl = clauses.get(i);
            boolean target = isPositive == (polarity.get(i) == +1);
            int tBit = target ? 1 : 0;
            double p = ((double) feedbackT + (1 - 2 * tBit) * score) / (2.0 * feedbackT);
            p = Math.max(0.0, Math.min(1.0, p));
            if (rng.nextDouble() >= p) continue;
            if (fired[i]) {
                if (!target) typeTwo(cl, x);
                else if (countIncludes(cl) <= maxIncludedLiterals()) typeOne(cl, x);
                else typeOneGrowth(cl, x); // over-inclusive → decay path
            } else if (target) {
                typeOneGrowth(cl, x);
            }
        }
    }

    /** Train on a batch with seeded per-epoch shuffling. */
    public void trainBatch(long[][] inputs, boolean[] labels, int epochs) {
        if (inputs.length != labels.length) throw new IllegalArgumentException("length mismatch");
        Integer[] order = new Integer[inputs.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        for (int e = 0; e < epochs; e++) {
            Collections.shuffle(Arrays.asList(order), rng);
            for (int idx : order) trainStep(inputs[idx], labels[idx]);
        }
    }

    /** Class-1 iff the summed polarised vote is strictly positive. */
    public boolean predict(long packedInput) {
        int score = 0;
        for (int i = 0; i < clauses.size(); i++)
            if (fires(clauses.get(i), packedInput)) score += polarity.get(i);
        return score > 0;
    }

    private static long pack(long[] words) { return words[0]; }

    /** Exact distillation of the decision function into CLAUSESET. */
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
    public ClauseSetForm toClauseSet(String provenance) { return toDecisionClauseSet(provenance); }

    public int clauseCount() { return clauses.size(); }
    public int inputBits() { return inputBits; }
    public int nStates() { return nStates; }
}
