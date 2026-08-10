package io.matrix.tsetlin;

import io.matrix.bir.ClauseSetForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TsetlinTrainer — primary BIR producer (SPEC-002 Stage B).
 *
 * <p>Learns ClauseSetForm from labeled binary examples via Tsetlin automata.
 * Each clause has one automaton per input bit; the automaton decides whether
 * to include the literal (positive or negative) in the clause.
 *
 * <p>Training loop:
 * <ol>
 *   <li>Present example (positive or negative)</li>
 *   <li>For each clause, compute its output</li>
 *   <li>Apply Type I feedback for positive examples, Type II for negative</li>
 *   <li>Automata converge to stable literal selections</li>
 * </ol>
 */
public final class TsetlinTrainer {

    private final int inputBits;
    private final int nClauses;
    private final int nStates;
    private final List<TsetlinAutomaton[]> clauseAutomata; // [clause][bit] → automaton
    private final Random rng;

    public TsetlinTrainer(int inputBits, int nClauses, int nStates, Random rng) {
        this.inputBits = inputBits;
        this.nClauses = nClauses;
        this.nStates = nStates;
        this.rng = rng;
        this.clauseAutomata = new ArrayList<>();
        for (int c = 0; c < nClauses; c++) {
            TsetlinAutomaton[] arr = new TsetlinAutomaton[inputBits];
            for (int b = 0; b < inputBits; b++) {
                arr[b] = new TsetlinAutomaton(nStates);
                // Random init: some automata start in "include" state
                if (rng.nextBoolean()) {
                    arr[b].reward(); // push to include
                }
            }
            clauseAutomata.add(arr);
        }
    }

    /** Train on a single labeled example. */
    public void trainStep(long[] input, boolean isPositive) {
        for (int c = 0; c < nClauses; c++) {
            TsetlinAutomaton[] automata = clauseAutomata.get(c);
            boolean clauseFires = evaluateClause(automata, input);

            for (int b = 0; b < inputBits; b++) {
                boolean bitSet = ((input[b >>> 6] >>> (b & 63)) & 1L) == 1L;
                boolean included = automata[b].action();

                if (isPositive) {
                    // Type I: reward inclusion of present literals, penalize inclusion of absent
                    if (bitSet) {
                        if (included) automata[b].reward();
                        else automata[b].penalize();
                    } else {
                        if (included) automata[b].penalize();
                        // absent + not included → no change
                    }
                } else {
                    // Type II: penalize inclusion of literals present in negative examples
                    if (bitSet && included) {
                        automata[b].penalize();
                    }
                }
            }
        }
    }

    /** Train on a batch of examples. */
    public void trainBatch(long[][] inputs, boolean[] labels, int epochs) {
        for (int e = 0; e < epochs; e++) {
            for (int i = 0; i < inputs.length; i++) {
                trainStep(inputs[i], labels[i]);
            }
        }
    }

    /** Evaluate a clause on input. */
    private boolean evaluateClause(TsetlinAutomaton[] automata, long[] input) {
        for (int b = 0; b < inputBits; b++) {
            boolean bitSet = ((input[b >>> 6] >>> (b & 63)) & 1L) == 1L;
            boolean included = automata[b].action();
            if (included && !bitSet) return false; // positive literal not satisfied
            // Note: negative literals (NOT x) are handled by automaton state
            // state > N means "include x", state ≤ N means "exclude x"
        }
        return true;
    }

    /** Export learned clauses as ClauseSetForm. */
    public ClauseSetForm toClauseSet(String provenance) {
        List<ClauseSetForm.Clause> clauses = new ArrayList<>();
        int kWords = (inputBits + 63) / 64;
        for (int c = 0; c < nClauses; c++) {
            TsetlinAutomaton[] automata = clauseAutomata.get(c);
            long[] pos = new long[kWords];
            long[] neg = new long[kWords];
            for (int b = 0; b < inputBits; b++) {
                if (automata[b].action()) {
                    pos[b >>> 6] |= (1L << (b & 63));
                } else {
                    neg[b >>> 6] |= (1L << (b & 63));
                }
            }
            clauses.add(new ClauseSetForm.Clause(pos, neg));
        }
        return new ClauseSetForm(inputBits, clauses, provenance, 1.0);
    }

    /** Current clause count. */
    public int clauseCount() { return nClauses; }
    public int inputBits() { return inputBits; }
}
