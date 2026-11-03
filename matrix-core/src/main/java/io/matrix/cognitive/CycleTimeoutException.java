package io.matrix.cognitive;

/**
 * W45.0 — the cognitive cycle ran out of its budget.
 *
 * <p>This is the one exception the cycle raises on purpose. It is thrown at a phase boundary
 * when the deadline has passed, and it is caught by the cycle itself, which turns it into an
 * explicit terminal outcome: the episode is closed, the trace is persisted with the timeout
 * recorded, and the caller is told that the cycle did not finish.</p>
 *
 * <p><b>What a timeout does NOT do.</b> It does not roll back the work that was already done.
 * An observation that was committed before the deadline is committed; the episode says so. A
 * cycle that pretended its earlier work had not happened would be a cycle that lies about the
 * world, and the whole point of the timeout is that the record of what happened stays
 * truthful.</p>
 */
public class CycleTimeoutException extends RuntimeException {

    private final String phase;
    private final long elapsedMillis;
    private final long budgetMillis;

    /**
     * @param phase        the phase that found the budget exhausted
     * @param elapsedMillis how long the cycle had been running
     * @param budgetMillis  the budget it was given
     */
    public CycleTimeoutException(String phase, long elapsedMillis, long budgetMillis) {
        super("the cognitive cycle ran out of its " + budgetMillis + "ms budget at the "
                + phase + " phase, after " + elapsedMillis + "ms");
        this.phase = phase;
        this.elapsedMillis = elapsedMillis;
        this.budgetMillis = budgetMillis;
    }

    /** The phase that found the budget exhausted. */
    public String phase() {
        return phase;
    }

    /** How long the cycle had been running. */
    public long elapsedMillis() {
        return elapsedMillis;
    }

    /** The budget the cycle was given. */
    public long budgetMillis() {
        return budgetMillis;
    }
}
