package io.matrix.benchmark.w51;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * W51 §10 — how many bytes a thread allocated.
 *
 * <p><b>Why allocation is measured at all.</b> A latency number says how long a path took; it
 * does not say whether the path is cheap or expensive. A path that takes a microsecond and
 * allocates a megabyte is a different path from one that takes a microsecond and allocates
 * nothing, and the second one is what decides whether the first can run a million times a
 * second. The JVM already counts the bytes a thread allocated; this class is the door to
 * that counter.</p>
 *
 * <p><b>Why it is the allocating thread that is counted.</b> The counter is per thread, and
 * the measured action runs on the thread that calls it. Counting the whole process would
 * include whatever else the machine was doing, and the number would move with the load of
 * the machine rather than with the code.</p>
 *
 * <p><b>Why it may be unavailable.</b> The counter is an extension of the platform's thread
 * bean, and not every JVM provides it. When it is missing this class says so
 * ({@link #supported()}) and the measurement reports
 * {@link BenchResult#UNSUPPORTED_ALLOCATIONS} — a number that is missing, not a zero. A zero
 * would claim the path allocates nothing, which is a different statement and a false one.</p>
 *
 * <p><b>Why the counter is enabled here.</b> The per-thread allocation counter is off by
 * default. Enabling it costs the JVM something, so it is enabled once, by this class, for
 * the thread that measures — and the measurements that matter are the ones taken after the
 * warm-up, by which time the enabling cost is behind us.</p>
 */
public final class AllocationMeter {

    private final ThreadMXBean platformBean;
    private final com.sun.management.ThreadMXBean allocatingBean;

    /**
     * A meter over the calling thread.
     */
    public AllocationMeter() {
        ThreadMXBean platform = ManagementFactory.getThreadMXBean();
        this.platformBean = platform;
        com.sun.management.ThreadMXBean allocating = null;
        if (platform instanceof com.sun.management.ThreadMXBean candidate
                && candidate.isThreadAllocatedMemorySupported()) {
            candidate.setThreadAllocatedMemoryEnabled(true);
            allocating = candidate;
        }
        this.allocatingBean = allocating;
    }

    /** True when this JVM reports the per-thread allocation. */
    public boolean supported() {
        return allocatingBean != null;
    }

    /**
     * The bytes the calling thread has allocated so far.
     *
     * @return the byte count, or {@link BenchResult#UNSUPPORTED_ALLOCATIONS} when the JVM does
     *         not report it
     */
    public long allocatedBytes() {
        if (allocatingBean == null) {
            return BenchResult.UNSUPPORTED_ALLOCATIONS;
        }
        long bytes = allocatingBean.getThreadAllocatedBytes(Thread.currentThread().getId());
        return bytes < 0 ? BenchResult.UNSUPPORTED_ALLOCATIONS : bytes;
    }

    /**
     * The platform's thread bean, for the measurements that need the platform's own view.
     *
     * @return the bean
     */
    public ThreadMXBean platformBean() {
        return platformBean;
    }
}
