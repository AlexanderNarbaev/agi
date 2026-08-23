package io.matrix.tsetlin;

/**
 * Single Tsetlin automaton over {@code 2N} states.
 *
 * <p>State space {@code [1..2N]}: states {@code 1..N} mean the corresponding
 * literal is EXCLUDED from its clause, states {@code N+1..2N} mean INCLUDED.
 * Directions are fixed by meaning: {@link #reward()} always moves one step
 * toward INCLUDE, {@link #penalty()} one step toward EXCLUDE;
 * {@link #includeNow()} jumps to the first include state (Type II feedback,
 * Granmo-style).
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

    /** One step toward INCLUDE (saturates at {@code 2n}). */
    public void reward() {
        state = Math.min(2 * n, state + 1);
    }

    /** One step toward EXCLUDE (saturates at {@code 1}). */
    public void penalty() {
        state = Math.max(1, state - 1);
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
     * Flat Type I: literal present ⇒ reinforce toward include
     * ({@code reward()}), absent ⇒ push toward exclude ({@code penalty()}).
     */
    public void feedbackTypeI(boolean literalPresent) {
        if (literalPresent) reward(); else penalty();
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
