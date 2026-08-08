package io.matrix.tsetlin;

/**
 * Tsetlin Automaton — 2N-state learning automaton for clause construction.
 *
 * <p>Per SPEC-002 §2 / DESIGN-04: Tsetlin automata are the primary learning
 * mechanism for BIR production. Each automaton has 2N states: states 1..N
 * mean "exclude literal", states N+1..2N mean "include literal".
 *
 * <p>Feedback types:
 * <ul>
 *   <li>Type I (recognition): reward inclusion of literals that appear in
 *       positive examples; penalize inclusion of literals that don't.</li>
 *   <li>Type II (rejection): penalize inclusion of literals that appear in
 *       negative examples.</li>
 * </ul>
 *
 * <p>Monotonicity: rewards never decrease the automaton's state, penalties
 * never increase it — guaranteeing convergence to a stable configuration.
 */
public final class TsetlinAutomaton {

    private final int nStates; // N (half of 2N)
    private int state;         // current state 1..2N

    public TsetlinAutomaton(int nStates) {
        if (nStates < 1) throw new IllegalArgumentException("nStates >= 1");
        this.nStates = nStates;
        this.state = nStates; // start at boundary (exclude)
    }

    public int state() { return state; }
    public int nStates() { return nStates; }

    /** Current action: true = include literal, false = exclude. */
    public boolean action() { return state > nStates; }

    /** Reward: move toward inclusion. */
    public void reward() {
        if (state < 2 * nStates) state++;
    }

    /** Penalize: move toward exclusion. */
    public void penalize() {
        if (state > 1) state--;
    }

    /** Type I feedback (recognition): reward if literal present, penalize if absent. */
    public void feedbackTypeI(boolean literalPresent) {
        if (literalPresent) reward(); else penalize();
    }

    /** Type II feedback (rejection): penalize if literal present in negative example. */
    public void feedbackTypeII(boolean literalPresent) {
        if (literalPresent) penalize();
    }
}
