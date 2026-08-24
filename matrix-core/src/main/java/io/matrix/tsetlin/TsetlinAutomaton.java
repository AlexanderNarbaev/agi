package io.matrix.tsetlin;

/**
 * Tsetlin automaton — faithful arithmetic-counter port of the reference
 * implementation (cair/pyTsetlinMachine ConvolutionalTsetlinMachine.c).
 *
 * <p>State is an unsigned counter {@code [0..2^nStates-1]}: INCLUDED iff
 * {@code state >= n} (top bit set), otherwise EXCLUDED. Operations are
 * direction-fixed arithmetic: {@link #inc()} = +1 saturating at 2n,
 * {@link #dec()} = −1 floored at 0. Reference init: counter 0 (deepest
 * exclude) — tm_initialize writes low bits ~0 and top bit 0.
 */
public final class TsetlinAutomaton {

    private final int n;
    private int state;

    /** Legacy ctor: starts at exclude-saturation (counter 0). */
    public TsetlinAutomaton(int n) {
        this(n, 0);
    }

    /**
     * @param rawState raw counter value {@code [0..2n]} (0 = deepest exclude,
     *                 2n = deepest include); differs from the old 1-based
     *                 side notation by design.
     */
    public TsetlinAutomaton(int n, int rawState) {
        if (n < 1) throw new IllegalArgumentException("n >= 1");
        if (rawState < 0 || rawState > 2 * n) throw new IllegalArgumentException("rawState in 0.." + (2 * n));
        this.n = n;
        this.state = rawState;
    }

    public int state() { return state; }

    public boolean includes() { return state >= n; }

    /** +1 toward include, saturating at {@code 2n}. */
    public void inc() {
        if (state < 2 * n) state++;
    }

    /** −1 toward exclude, floored at {@code 0}. */
    public void dec() {
        if (state > 0) state--;
    }

    /** Jump straight to the first include position {@code n}. */
    public void includeNow() {
        state = n;
    }

    // ─── Compatibility aliases ───

    /** @return {@link #includes()} */
    public boolean action() { return includes(); }

    /** Alias for {@link #dec()}. */
    public void penalize() { dec(); }

    /** Alias for {@link #dec()} (legacy name). */
    public void penalty() { dec(); }

    /** Legacy alias: {@link #inc()} toward include. */
    public void reward() { inc(); }

    /**
     * Flat Type I: literal present ⇒ inc; absent ⇒ dec.
     */
    public void feedbackTypeI(boolean literalPresent) {
        if (literalPresent) inc(); else dec();
    }

    /**
     * Flat Type II: present-in-negative & included ⇒ drop out;
     * absent & excluded ⇒ step toward inclusion.
     */
    public void feedbackTypeII(boolean literalPresentInNegative) {
        if (literalPresentInNegative) {
            if (includes()) dec();
        } else if (!includes()) {
            inc();
        }
    }
}
