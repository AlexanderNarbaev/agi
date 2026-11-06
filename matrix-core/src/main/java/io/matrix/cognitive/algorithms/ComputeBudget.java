package io.matrix.cognitive.algorithms;

/**
 * W44.2 — the resources a computation is allowed to spend.
 *
 * <p>A task declares its estimated cost and the scheduler admits, delays, shrinks or rejects work
 * according to the available budget. Without a budget there is no "too expensive", and the W44
 * directive is explicit that MATRIX must not spend all available compute merely because it is
 * available.</p>
 *
 * <p>Every limit is explicit and finite. A budget with an infinite field is a missing budget, so
 * the constructor refuses it: {@link #unbounded()} exists so that "no limit" is a DELIBERATE
 * choice with a name, not an oversight.</p>
 *
 * <p>The budget is a DECLARATION, not an enforcement mechanism. A method that exceeds it must
 * report that it did — the selector and the trace record the overrun rather than hiding it.</p>
 */
public record ComputeBudget(
        long maxWallClockMillis,
        long maxCpuMillis,
        long maxMemoryBytes,
        int maxSearchNodes) {

    /** The smallest non-zero wall-clock budget, in milliseconds. */
    public static final long MINIMUM_WALL_CLOCK_MILLIS = 1L;

    public ComputeBudget {
        if (maxWallClockMillis < 0) {
            throw new IllegalArgumentException("wall clock budget must not be negative");
        }
        if (maxCpuMillis < 0) {
            throw new IllegalArgumentException("cpu budget must not be negative");
        }
        if (maxMemoryBytes < 0) {
            throw new IllegalArgumentException("memory budget must not be negative");
        }
        if (maxSearchNodes < 0) {
            throw new IllegalArgumentException("search node budget must not be negative");
        }
    }

    /** A budget with no limits at all — explicitly chosen, never the default. */
    public static ComputeBudget unbounded() {
        return new ComputeBudget(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE);
    }

    /** A budget expressed only in wall-clock time, which is what most methods actually need. */
    public static ComputeBudget millis(long wallClockMillis) {
        return new ComputeBudget(wallClockMillis, wallClockMillis, Long.MAX_VALUE, Integer.MAX_VALUE);
    }

    /** True when the budget allows a computation of the given cost. */
    public boolean permits(long estimatedMillis, int estimatedNodes) {
        return estimatedMillis <= maxWallClockMillis && estimatedNodes <= maxSearchNodes;
    }
}
