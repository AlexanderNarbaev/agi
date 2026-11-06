package io.matrix.cognitive;

import java.util.function.LongSupplier;

/**
 * W45.0 — the deadline of one cognitive cycle.
 *
 * <p><b>Why this exists.</b> The W44 wave declared a cycle budget of
 * {@link CognitiveKernel#DEFAULT_CYCLE_BUDGET_MILLIS} milliseconds and carried it as a
 * {@link io.matrix.cognitive.algorithms.ComputeBudget}. Nothing ever compared the elapsed time
 * with it: the budget was a number in a record, not a bound on anything. The repository
 * archaeology found the same pattern in six of the seven budget mechanisms it inspected, and
 * only three were enforced at runtime.</p>
 *
 * <p><b>A deadline is a resource boundary, not a cognitive input.</b> The project rule forbids
 * a wall-clock read in a runtime DECISION path, because a decision that depends on WHEN it was
 * taken depends on the clock rather than on what is true. A budget is not such a decision: the
 * question "has the work already taken too long?" has the same answer whatever the wall clock
 * says, and a system that cannot stop is a system that can be held hostage by one slow input.
 * The clock here is therefore MONOTONIC and it is read only for the resource check.</p>
 *
 * <p><b>What it does and does not promise.</b> It promises that the cycle checks the boundary
 * at the phase boundaries, and that a cycle which has run out of budget ends in an explicit,
 * persisted terminal state. It does NOT promise a hard wall-clock bound on any single
 * operation: an operation that cannot be interrupted runs to completion, and the deadline is
 * checked after it. The operations that cannot be interrupted are named in
 * {@link io.matrix.cognitive.algorithms.ComputeBudget} and in the W45 integrity design, because
 * a system that claims a bound it does not have is worse than one that says it has none.</p>
 */
public final class CycleDeadline {

    /** The number of nanoseconds in a millisecond. */
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final long budgetNanos;
    private final LongSupplier clock;
    private final long startedAtNanos;
    private final String phase;

    /**
     * A deadline over a budget in milliseconds.
     *
     * @param budgetMillis the budget, in milliseconds
     * @param clock        the monotonic clock, in nanoseconds
     */
    public CycleDeadline(long budgetMillis, LongSupplier clock) {
        if (budgetMillis < 0) {
            throw new IllegalArgumentException("a cycle budget must not be negative");
        }
        if (clock == null) {
            throw new IllegalArgumentException("a deadline needs a clock");
        }
        this.budgetNanos = budgetMillis * NANOS_PER_MILLI;
        this.clock = clock;
        this.startedAtNanos = clock.getAsLong();
        this.phase = "the cycle's entry";
    }

    /**
     * The time the cycle has been running, in milliseconds.
     *
     * @return the elapsed time
     */
    public long elapsedMillis() {
        return (clock.getAsLong() - startedAtNanos) / NANOS_PER_MILLI;
    }

    /**
     * The time the cycle has left, in milliseconds. Never negative.
     *
     * @return the remaining budget
     */
    public long remainingMillis() {
        long remaining = budgetNanos - (clock.getAsLong() - startedAtNanos);
        return remaining <= 0 ? 0L : remaining / NANOS_PER_MILLI;
    }

    /**
     * Whether the budget is exhausted.
     *
     * @return true when the cycle has run out of time
     */
    public boolean expired() {
        return clock.getAsLong() - startedAtNanos >= budgetNanos;
    }

    /**
     * Checks the boundary at a phase boundary.
     *
     * @param phase the phase that has just finished
     * @throws CycleTimeoutException when the budget is exhausted
     */
    public void check(String phase) {
        if (expired()) {
            throw new CycleTimeoutException(phase, elapsedMillis(), budgetNanos / NANOS_PER_MILLI);
        }
    }

    /**
     * The budget as a compute budget, so that the algorithms the cycle runs are told the time
     * that is left rather than the time the cycle was given.
     *
     * @return the remaining budget
     */
    public io.matrix.cognitive.algorithms.ComputeBudget remainingComputeBudget() {
        return io.matrix.cognitive.algorithms.ComputeBudget.millis(remainingMillis());
    }
}
