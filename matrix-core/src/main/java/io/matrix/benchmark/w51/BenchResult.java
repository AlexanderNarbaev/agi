package io.matrix.benchmark.w51;

/**
 * W51 §10 — what one measurement says.
 *
 * <p><b>Why a record and not a printed line.</b> A measurement that is printed cannot be
 * compared with the next one, and a number that only exists in a console cannot be cited in a
 * document without being copied by hand — which is how a number stops matching the artefact
 * it came from. A record is a value: it can be held, compared, serialised and asserted on.</p>
 *
 * <p><b>What the numbers mean.</b> The timings are <i>per call</i> of the measured action.
 * The average is the arithmetic mean of the measured samples; the percentiles are the
 * nearest-rank percentiles of the same samples (see {@link MicroBench}); the throughput is
 * the number of calls the measured window could carry. The allocation figure is the number of
 * bytes the measured thread allocated per call, and it is {@link #UNSUPPORTED_ALLOCATIONS}
 * when the JVM does not report per-thread allocation — a number that is missing must not be
 * a zero, because a zero would say "this allocates nothing", which is a different claim.</p>
 *
 * <p><b>Why the sample count is in the record.</b> A percentile computed from ten samples is
 * not a percentile; it is the maximum, wearing a percentile's name. The count travels with
 * the numbers so that a reader can see how much of the distribution was actually observed.</p>
 *
 * @param name                    what was measured
 * @param samples                 how many measured samples the numbers are computed from
 * @param averageNanos            the arithmetic mean of one call, in nanoseconds
 * @param p50Nanos                the median of the samples, in nanoseconds
 * @param p90Nanos                the ninetieth percentile of the samples, in nanoseconds
 * @param p99Nanos                the ninety-ninth percentile of the samples, in nanoseconds
 * @param minNanos                the fastest sample, in nanoseconds
 * @param maxNanos                the slowest sample, in nanoseconds
 * @param throughputPerSecond     the calls per second the measured window carried
 * @param allocatedBytesPerCall   the bytes the measured thread allocated per call
 */
public record BenchResult(
        String name,
        int samples,
        double averageNanos,
        long p50Nanos,
        long p90Nanos,
        long p99Nanos,
        long minNanos,
        long maxNanos,
        double throughputPerSecond,
        long allocatedBytesPerCall) {

    /** The value used when the JVM does not report per-thread allocation. */
    public static final long UNSUPPORTED_ALLOCATIONS = -1L;

    /** The nanoseconds in one second. DERIVED from the time unit definition. */
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    public BenchResult {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a measurement needs a name");
        }
        if (samples < 1) {
            throw new IllegalArgumentException("a measurement needs at least one sample");
        }
    }

    /** True when the JVM reported the per-thread allocation. */
    public boolean allocationMeasured() {
        return allocatedBytesPerCall != UNSUPPORTED_ALLOCATIONS;
    }

    /** The average duration of one call, in microseconds. */
    public double averageMicros() {
        return averageNanos / 1000.0;
    }

    /** The ninety-ninth percentile, in microseconds. */
    public double p99Micros() {
        return p99Nanos / 1000.0;
    }

    @Override
    public String toString() {
        return name + "[" + samples + " samples, avg " + averageMicros() + "us, p50 "
                + (p50Nanos / 1000.0) + "us, p90 " + (p90Nanos / 1000.0) + "us, p99 "
                + p99Micros() + "us, " + throughputPerSecond + " calls/s, "
                + (allocationMeasured() ? allocatedBytesPerCall + " B/call" : "allocations unknown")
                + "]";
    }
}
