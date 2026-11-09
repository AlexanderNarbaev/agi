package io.matrix.cognitive.algorithms;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * W44.2 — what each method actually cost, measured.
 *
 * <p>The selector is told to score methods on DATA. The accuracy half of that data is the
 * capability's {@link AlgorithmCapability.AccuracyProfile}; the cost half is what this ledger
 * records. A method nobody has run has no measured cost, and the selector falls back to the
 * capability's declared complexity for it — but only for that method, and the decision says which
 * basis was used for each candidate.</p>
 *
 * <p><b>The clock is read in exactly one place in this package</b> — inside {@link #measure}. That
 * is deliberate. A cost that is read during a decision would make the decision depend on how fast
 * the machine happened to be, and the same request could select a different method on a loaded
 * box. Here the cost is measured AFTER the work, in a separate call, and it only ever informs
 * LATER selections. The selector never measures while it decides.</p>
 *
 * <p>Thread safety: the maps are concurrent; a recording races with another recording of the same
 * method by losing one of the two updates to the counter, which is acceptable because the value
 * is an average used for ranking, not an accounting total.</p>
 */
public final class CostLedger {

    /**
     * The value {@link #measuredNanos(String)} returns for a method that has never been measured.
     *
     * <p>DERIVED: a negative value cannot be a real duration, so it cannot be confused with a
     * method that was measured at zero nanoseconds.</p>
     */
    public static final long UNMEASURED = -1L;

    /** The nanoseconds in one millisecond. DERIVED from the time unit definition. */
    private static final long NANOS_PER_MILLI = 1_000_000L;

    /**
     * What was measured for one method.
     *
     * @param methodId  the method the measurement belongs to
     * @param totalNanos the summed duration of every recorded call
     * @param calls     how many calls were recorded
     * @param successes how many of them completed without an exception
     * @param failures  how many of them threw
     */
    public record Measurement(String methodId, long totalNanos, int calls, int successes, int failures) {

        public Measurement {
            if (methodId == null || methodId.isBlank()) {
                throw new IllegalArgumentException("method id must not be blank");
            }
        }

        /** The mean duration of one call, in nanoseconds. */
        public double meanNanos() {
            return calls == 0 ? 0.0 : (double) totalNanos / (double) calls;
        }

        /** The mean duration of one call, in milliseconds. */
        public double meanMillis() {
            return meanNanos() / (double) NANOS_PER_MILLI;
        }
    }

    private final ConcurrentMap<String, Measurement> measurements = new ConcurrentHashMap<>();

    /**
     * W52 — where the measurement is kept when it must outlive the process.
     *
     * <p>Null means the ledger is process-local, which is the right thing for a test and
     * the wrong thing for a running system: a system that forgets how its own methods
     * behave at every restart falls back to the declared complexity of each method, which
     * is a person's guess where a measurement belongs.</p>
     */
    private final MethodMeasurementStore store;

    /**
     * W52 — the workspace the measurements belong to.
     */
    private final String workspaceId;

    /**
     * The clock the durable record is stamped with.
     */
    private final java.util.function.Supplier<String> timestamps;

    /**
     * A ledger that lives and dies with the process.
     */
    public CostLedger() {
        this(null, null, () -> java.time.Instant.now().toString());
    }

    /**
     * W52 — a ledger whose measurements outlive the process.
     *
     * <p>The measurements already recorded are read back at construction, so a process that
     * starts knows what its own methods cost before it has run any of them.</p>
     *
     * @param store      where the measurements are kept, or null for a process-local ledger
     * @param workspaceId the workspace the measurements belong to
     */
    public CostLedger(MethodMeasurementStore store, String workspaceId) {
        this(store, workspaceId, () -> java.time.Instant.now().toString());
    }

    /**
     * W52 — a ledger whose measurements outlive the process, with the clock named.
     *
     * @param store       where the measurements are kept, or null for a process-local ledger
     * @param workspaceId the workspace the measurements belong to
     * @param timestamps  the clock the durable record is stamped with
     */
    public CostLedger(MethodMeasurementStore store, String workspaceId,
                      java.util.function.Supplier<String> timestamps) {
        if (store != null && (workspaceId == null || workspaceId.isBlank())) {
            throw new IllegalArgumentException(
                    "a durable ledger needs the workspace its measurements belong to");
        }
        this.store = store;
        this.workspaceId = workspaceId;
        this.timestamps = timestamps == null
                ? () -> java.time.Instant.now().toString() : timestamps;
        if (store != null) {
            for (Measurement measurement : store.measurements(workspaceId)) {
                measurements.put(measurement.methodId(), measurement);
            }
        }
    }

    /**
     * True when the measurements of this ledger outlive the process.
     *
     * @return true when the ledger is durable
     */
    public boolean durable() {
        return store != null;
    }

    /**
     * Records one measured call.
     *
     * @param methodId  the method that was run
     * @param nanos     how long it took, in nanoseconds
     * @param succeeded whether it completed without throwing
     */
    public void record(String methodId, long nanos, boolean succeeded) {
        if (methodId == null || methodId.isBlank()) {
            throw new IllegalArgumentException("method id must not be blank");
        }
        if (nanos < 0) {
            throw new IllegalArgumentException("measured nanoseconds must not be negative");
        }
        measurements.compute(methodId, (id, existing) -> {
            if (existing == null) {
                return new Measurement(id, nanos, 1, succeeded ? 1 : 0, succeeded ? 0 : 1);
            }
            return new Measurement(id,
                    existing.totalNanos() + nanos,
                    existing.calls() + 1,
                    existing.successes() + (succeeded ? 1 : 0),
                    existing.failures() + (succeeded ? 0 : 1));
        });
        if (store != null) {
            store.recordMeasurement(workspaceId, methodId, nanos, succeeded, timestamps.get());
        }
    }

    /**
     * The mean measured duration of a method, in nanoseconds.
     *
     * @return the mean, or {@link #UNMEASURED} when the method has never been measured
     */
    public long measuredNanos(String methodId) {
        Measurement m = methodId == null ? null : measurements.get(methodId);
        return m == null ? UNMEASURED : (long) m.meanNanos();
    }

    /** The full measurement record for a method, or {@code null} when it has never been measured. */
    public Measurement measurement(String methodId) {
        return methodId == null ? null : measurements.get(methodId);
    }

    /** How many calls were recorded for a method. */
    public int callCount(String methodId) {
        Measurement m = methodId == null ? null : measurements.get(methodId);
        return m == null ? 0 : m.calls();
    }

    /** The ids of every method with at least one measurement. */
    public java.util.Set<String> measuredMethodIds() {
        return java.util.Set.copyOf(measurements.keySet());
    }

    /** Forgets every measurement. */
    public void clear() {
        measurements.clear();
    }

    /**
     * Runs a body, records how long it took, and returns what it produced.
     *
     * <p>This is the only method in the package that reads a clock, and it is the reason the
     * package can promise that a decision never depends on timing: the cost is attached to a
     * finished call, after the fact.</p>
     *
     * @param methodId the method being measured
     * @param body     the work to run
     * @return whatever the body returned
     */
    public <T> T measure(String methodId, Supplier<T> body) {
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }
        long startedAt = System.nanoTime();
        boolean succeeded = true;
        try {
            return body.get();
        } catch (RuntimeException | Error e) {
            succeeded = false;
            throw e;
        } finally {
            record(methodId, System.nanoTime() - startedAt, succeeded);
        }
    }

    /**
     * Runs a body, records how long it took, and returns what it produced.
     *
     * <p>The void counterpart of {@link #measure(String, Supplier)}.</p>
     */
    public void measure(String methodId, Runnable body) {
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }
        measure(methodId, () -> {
            body.run();
            return null;
        });
    }

    /** A snapshot of every measurement, for a trace or a report. */
    public Map<String, Measurement> snapshot() {
        return Map.copyOf(new LinkedHashMap<>(measurements));
    }
}
