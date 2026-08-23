package io.matrix.tsetlin;

/**
 * Single Tsetlin automaton over {@code 2N} states.
 *
 * <p>State space {@code [1..2N]}: states {@code 1..N} mean the corresponding
 * literal is EXCLUDED from its clause, states {@code N+1..2N} mean INCLUDED.
 * Canonical Granmo dynamics: {@link #reward()} deepens the automaton into its
 * CURRENT action's extreme; {@link #penalty()} takes one step toward the
 * opposite side; {@link #includeNow()} jumps to the first include state.
 */
public final class TsetlinAutomaton {

    private final int n;
    private int state;

    /** Starts at state {@code n} (top of the exclude side). */
    public TsetlinAutomaton(int n) {
        this(n, n);
    }

    /** @param n half-state count ({@code n >= 1}); total states = 2n */
    public TsetlinAutomaton(int n, int initialState) {
        if (n < 1) throw new IllegalArgumentException("n >= 1");
        if (initialState < 1 || initialState > 2 * n) {
            throw new IllegalArgumentException("initialState in 1.." + (2 * n));
        }
        this.n = n;
        this.state = initialState;
    }

    public int state() { return state; }

    public boolean includes() { return state > n; }

    /** Deepen into the CURRENT action's side (canonical TM reward). */
    public void reward() {
        if (state <= n) state = Math.max(1, state - 1);
        else state = Math.min(2 * n, state + 1);
    }

    /** One step toward the OPPOSITE side (canonical TM penalty). */
    public void penalty() {
        if (state <= n) state = Math.min(2 * n, state + 1);
        else state = Math.max(1, state - 1);
    }

    /** Jump straight to the first INCLUDE state (Type II feedback). */
    public void includeNow() {
        state = n + 1;
    }

    // ─── Compatibility aliases (legacy producer/test API) ───

    /** @return {@link #includes()} */
    public boolean action() { return includes(); }

    /** Alias for {@link #penalty()}. */
    public void penalize() { penalty(); }

    /**
     * Flat Type I (canonical rows): a TRUE-valued literal that is excluded
     * takes one step toward inclusion; a FALSE-valued literal that is
     * included takes one step toward exclusion; already-consistent states
     * stay put in this flat helper.
     */
    public void feedbackTypeI(boolean literalPresent) {
        if (literalPresent && !includes()) penalty();
        else if (!literalPresent && includes()) penalty();
    }

    /**
     * Flat Type II: literal present in a negative example and currently
     * included ⇒ drop it; absent and excluded ⇒ pull in now.
     */
    public void feedbackTypeII(boolean literalPresentInNegative) {
        if (literalPresentInNegative) {
            if (includes()) penalty();
        } else if (!includes()) {
            includeNow();
        }
    }
}
