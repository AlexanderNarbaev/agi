package io.matrix.tsetlin;

import io.matrix.bir.ClauseSetForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TsetlinTrainer — primary BIR producer (SPEC-002 Stage B, FR-B1).
 *
 * <p>Learns a DNF (ClauseSetForm) from labeled binary examples via Tsetlin
 * automata. Each clause holds TWO automata per input bit: one for the
 * positive literal {@code x_j}, one for the negated literal {@code ¬x_j}.
 * An automaton in the include state adds its literal to the clause; an
 * excluded automaton contributes NOTHING (omission — never a negation).
 *
 * <p>Training per example {@code (x, y)} for every clause:
 * <ul>
 *   <li>{@code y = true} and clause fires → Type I: each included agreeing
 *       literal is deepened with probability {@code (s-1)/s}; each excluded
 *       literal whose value is TRUE under x is pushed toward inclusion with
 *       probability {@code 1/s}.</li>
 *   <li>{@code y = false} and clause fires → Type II: every excluded literal
 *       whose value is FALSE under x jumps into inclusion now, which flips
 *       this clause to 0 on repeated inputs.</li>
 *   <li>Non-firing clauses are left untouched.</li>
 * </ul>
 *
 * <p>Determinism: all stochasticity flows through the injected seeded
 * {@link Random}; identical seeds ⇒ identical trained states. Training is a
 * PRODUCER activity and lives outside the runtime decision contour
 * (CONSTITUTION II.2–3) — the runtime executes only the exported BIR.
 *
 * <p>Input encoding: bit-packed words ({@code long[]}), bit {@code j} of the
 * packed value is feature {@code j}; {@code inputBits <= 64} supported here.
 */
public final class TsetlinTrainer {

    /** Specificity parameter: larger ⇒ weaker exploration pressure. */
    private static final double S = 8.0;

    private final int inputBits;
    private final int nClauses;
    private final int nStates;
    private final List<TsetlinAutomaton[]> posAutomata; // [clause][bit]
    private final List<TsetlinAutomaton[]> negAutomata; // [clause][bit]
    private final Random rng;

    public TsetlinTrainer(int inputBits, int nClauses, int nStates, Random rng) {
        if (inputBits < 1 || inputBits > 64) {
            throw new IllegalArgumentException("inputBits in 1..64");
        }
        this.inputBits = inputBits;
        this.nClauses = nClauses;
        this.nStates = nStates;
        this.rng = rng;
        this.posAutomata = new ArrayList<>(nClauses);
        this.negAutomata = new ArrayList<>(nClauses);
        long seed = rng.nextLong();
        for (int c = 0; c < nClauses; c++) {
            posAutomata.add(newAutomata(inputBits, nStates, seed + 61L * c));
            negAutomata.add(newAutomata(inputBits, nStates, seed + 61L * c + 17));
        }
    }

    private static TsetlinAutomaton[] newAutomata(int k, int n, long seed) {
        TsetlinAutomaton[] arr = new TsetlinAutomaton[k];
        for (int j = 0; j < k; j++) {
            // Deterministic spread of initial exclude-side states.
            int offset = (int) (((seed >>> 4) + 31L * j) % n);
            arr[j] = new TsetlinAutomaton(n, 1 + offset);
        }
        return arr;
    }

    private static boolean bit(long packed, int j) {
        return ((packed >>> j) & 1L) == 1L;
    }

    private static long pack(long[] words) {
        return words[0];
    }

    private boolean evaluate(int c, long x) {
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            if (posAutomata.get(c)[j].includes() && !v) return false;
            if (negAutomata.get(c)[j].includes() && v) return false;
        }
        return true;
    }

    private void typeOne(int c, long x) {
        // Granmo Type Ia, fixed directions: reward ⇒ toward INCLUDE,
        // penalty ⇒ toward EXCLUDE. A fired clause guarantees every included
        // literal agrees with x, so the penalty branch only ever touches
        // excluded irrelevant literals.
        double pReward = (S - 1.0) / S;
        double pPenalty = 1.0 / S;
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            TsetlinAutomaton[] pa = posAutomata.get(c);
            TsetlinAutomaton[] na = negAutomata.get(c);
            if (v) {
                if (rng.nextDouble() < pReward) pa[j].reward();   // x_j true  → reinforce
                if (rng.nextDouble() < pPenalty) na[j].penalty(); // ¬x_j false → push out
            } else {
                if (rng.nextDouble() < pReward) na[j].reward();   // ¬x_j true → reinforce
                if (rng.nextDouble() < pPenalty) pa[j].penalty(); // x_j false → push out
            }
        }
    }

    private void typeTwo(int c, long x) {
        for (int j = 0; j < inputBits; j++) {
            boolean v = bit(x, j);
            TsetlinAutomaton[] pa = posAutomata.get(c);
            TsetlinAutomaton[] na = negAutomata.get(c);
            if (!pa[j].includes() && !v) pa[j].includeNow();
            if (!na[j].includes() && v) na[j].includeNow();
        }
    }

    /** Train on a single labeled example. */
    public void trainStep(long[] inputWords, boolean isPositive) {
        long x = pack(inputWords);
        for (int c = 0; c < nClauses; c++) {
            boolean fires = evaluate(c, x);
            if (isPositive && fires) typeOne(c, x);
            else if (!isPositive && fires) typeTwo(c, x);
        }
    }

    /** Train on a batch of examples for the given number of epochs. */
    public void trainBatch(long[][] inputs, boolean[] labels, int epochs) {
        if (inputs.length != labels.length) {
            throw new IllegalArgumentException("inputs/labels length mismatch");
        }
        for (int e = 0; e < epochs; e++) {
            for (int i = 0; i < inputs.length; i++) {
                trainStep(inputs[i], labels[i]);
            }
        }
    }

    /**
     * Exports the learned clauses as an exact DNF artifact: a literal enters
     * the clause iff its automaton is in the include state; omitted literals
     * stay absent from both masks (INV-2 range-checked by ClauseSetForm).
     */
    public ClauseSetForm toClauseSet(String provenance) {
        int kWords = (inputBits + 63) / 64;
        List<ClauseSetForm.Clause> clauses = new ArrayList<>(nClauses);
        for (int c = 0; c < nClauses; c++) {
            long[] pos = new long[kWords];
            long[] neg = new long[kWords];
            for (int b = 0; b < inputBits; b++) {
                if (posAutomata.get(c)[b].includes()) pos[b >>> 6] |= (1L << b);
                if (negAutomata.get(c)[b].includes()) neg[b >>> 6] |= (1L << b);
            }
            clauses.add(new ClauseSetForm.Clause(pos, neg));
        }
        return new ClauseSetForm(inputBits, clauses, provenance, 1.0);
    }

    /** Fires-any semantics of the exported DNF on packed input. */
    public boolean predict(long packedInput) {
        for (int c = 0; c < nClauses; c++) {
            if (evaluate(c, packedInput)) return true;
        }
        return false;
    }

    /** Current clause count. */
    public int clauseCount() { return nClauses; }

    public int inputBits() { return inputBits; }

    public int nStates() { return nStates; }
}
