package io.matrix.benchmark.w51;

import java.util.Arrays;

/**
 * W51 §10 — the harness the three hot paths are measured with.
 *
 * <p><b>Why a hand-written harness and not JMH.</b> JMH is the right tool, and it is not
 * available here: the environment has no network and no JMH artefacts in the Gradle cache, so
 * a benchmark that "needs JMH" is a benchmark that produces no numbers. This harness is
 * therefore written on {@link System#nanoTime()} with the two things that matter most for a
 * comparison to be honest — a warm-up that is not measured, and enough measured samples that
 * a percentile is a percentile.</p>
 *
 * <p><b>What this harness does NOT claim.</b> It is not a JMH-grade benchmark. It does not
 * control for the frequency scaling of the CPU, it does not fork a fresh JVM per sample,
 * and it does not report the confidence interval of the mean. What it does give is a
 * <i>comparison</i>: the same harness, the same inputs and the same machine, applied to
 * three paths, so that the numbers are comparable with each other. A comparison of the same
 * path across two machines is not something this harness supports, and the document that
 * reports these numbers says so.</p>
 *
 * <p><b>Why the warm-up is not measured.</b> The first calls of any Java path pay for the
 * class loading, the JIT compilation and the first-touch of every data structure the path
 * touches. Those costs belong to the program start, not to the path, and a measurement that
 * included them would describe the start rather than the path. The warm-up is therefore run
 * and discarded, and its size is a named constant rather than a number typed at the call
 * site.</p>
 *
 * <p><b>How the percentiles are computed.</b> The samples are sorted and the percentile is
 * taken by the <b>nearest-rank</b> rule: the p-th percentile is the sample at index
 * {@code ceil(p * n) - 1}. The rule is named because "the percentile" is otherwise ambiguous
 * — the interpolated rule and the nearest-rank rule give different numbers on the same
 * samples, and a reader comparing two reports cannot tell them apart unless the rule is
 * written down.</p>
 *
 * <p><b>How the allocation is measured.</b> The byte counter is read immediately before and
 * immediately after the measured action, so that the harness's own bookkeeping — filling the
 * sample array — is not counted against the action. The per-call figure is the sum of the
 * per-sample deltas divided by the number of samples.</p>
 */
public final class MicroBench {

    /**
     * The warm-up the fast paths use by default.
     *
     * <p>DERIVED from what the JIT needs: a few thousand calls are enough for the hot
     * methods to be compiled and for the profile to settle, and the paths measured here are
     * microsecond-scale, so a thousand calls cost a few milliseconds.</p>
     */
    public static final int DEFAULT_WARMUP_ROUNDS = 2_000;

    /**
     * The number of measured rounds the fast paths use by default.
     *
     * <p>DERIVED from the percentile: the nearest-rank p99 of n samples is the
     * {@code ceil(0.99 n)}-th smallest, so a p99 computed from a few hundred samples is
     * indistinguishable from the maximum. Five thousand samples put the p99 above the
     * fiftieth smallest sample of the tail, which is what makes the number worth reading.</p>
     */
    public static final int DEFAULT_MEASURED_ROUNDS = 5_000;

    /**
     * The warm-up the episode path uses.
     *
     * <p>An episode allocates a hypothesis space and a session of its own, so it touches far
     * more code than a decision does, and the first episodes pay for the loading of all of
     * it. Fewer rounds are needed for the same effect, because each round is so much heavier
     * than a decision.</p>
     */
    public static final int EPISODE_WARMUP_ROUNDS = 200;

    /**
     * The number of measured rounds the episode path uses.
     *
     * <p>Each round is an entire episode, so the count is bounded by how long a wave may
     * spend measuring: this is a few hundred episodes, not a few thousand.</p>
     */
    public static final int EPISODE_MEASURED_ROUNDS = 300;

    /** The fraction of the samples below which the median lies. */
    public static final double MEDIAN_FRACTION = 0.5;

    /** The fraction of the samples below which the ninetieth percentile lies. */
    public static final double P90_FRACTION = 0.9;

    /** The fraction of the samples below which the ninety-ninth percentile lies. */
    public static final double P99_FRACTION = 0.99;

    /** The nanoseconds in one second. DERIVED from the time unit definition. */
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private MicroBench() {
        throw new AssertionError("no instances");
    }

    /**
     * Measures an action.
     *
     * @param name          what is being measured
     * @param warmupRounds  how many calls are run and discarded
     * @param measuredRounds how many calls are measured
     * @param action        the action to measure
     * @return the result
     */
    public static BenchResult measure(String name, int warmupRounds, int measuredRounds,
                                       Runnable action) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a measurement needs a name");
        }
        if (warmupRounds < 0) {
            throw new IllegalArgumentException("the warm-up cannot be negative");
        }
        if (measuredRounds < 1) {
            throw new IllegalArgumentException("a measurement needs at least one round");
        }
        if (action == null) {
            throw new IllegalArgumentException("there is nothing to measure");
        }

        for (int i = 0; i < warmupRounds; i++) {
            action.run();
        }

        AllocationMeter meter = new AllocationMeter();
        boolean allocationsKnown = meter.supported();
        long[] samples = new long[measuredRounds];
        long allocatedTotal = 0;
        long windowStart = System.nanoTime();
        for (int i = 0; i < measuredRounds; i++) {
            long before = allocationsKnown ? meter.allocatedBytes() : 0L;
            long started = System.nanoTime();
            action.run();
            long elapsed = System.nanoTime() - started;
            long after = allocationsKnown ? meter.allocatedBytes() : 0L;
            samples[i] = elapsed;
            if (allocationsKnown) {
                allocatedTotal += after - before;
            }
        }
        long windowNanos = System.nanoTime() - windowStart;

        Arrays.sort(samples);
        double total = 0;
        for (long sample : samples) {
            total += sample;
        }
        double average = total / measuredRounds;
        double seconds = windowNanos / NANOS_PER_SECOND;
        double throughput = seconds > 0 ? measuredRounds / seconds : 0.0;
        long allocatedPerCall = allocationsKnown
                ? allocatedTotal / measuredRounds : BenchResult.UNSUPPORTED_ALLOCATIONS;

        return new BenchResult(name, measuredRounds, average, percentile(samples, MEDIAN_FRACTION),
                percentile(samples, P90_FRACTION), percentile(samples, P99_FRACTION), samples[0],
                samples[samples.length - 1], throughput, allocatedPerCall);
    }

    /**
     * The nearest-rank percentile of sorted samples.
     *
     * @param sorted   the samples, sorted ascending
     * @param fraction the fraction below which the percentile lies
     * @return the sample at the nearest rank
     */
    public static long percentile(long[] sorted, double fraction) {
        if (sorted == null || sorted.length == 0) {
            throw new IllegalArgumentException("there are no samples to take a percentile of");
        }
        if (fraction <= 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("a percentile fraction must be in (0, 1]");
        }
        int rank = (int) Math.ceil(fraction * sorted.length);
        int index = Math.min(Math.max(rank - 1, 0), sorted.length - 1);
        return sorted[index];
    }
}
