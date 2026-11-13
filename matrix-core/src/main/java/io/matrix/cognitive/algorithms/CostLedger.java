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
     * W53 — the verdicts of the calls, by stratum.
     *
     * <p>The cost of a method is pooled over the strata — it is a property of the method —
     * while the verdict of a call is a property of the method and of the request it answered.
     * The two are therefore kept apart.</p>
     */
    private final ConcurrentMap<Stratum, MethodVerdict> verdicts = new ConcurrentHashMap<>();

    /**
     * W53 — the stratum a verdict was observed in.
     *
     * @param methodId          the method
     * @param family            the family of the request
     * @param capabilityVersion the version of the capability
     */
    public record Stratum(String methodId, String family, String capabilityVersion) {

        public Stratum {
            if (methodId == null || methodId.isBlank()) {
                throw new IllegalArgumentException("the method of a stratum must not be blank");
            }
        }

        /**
         * The stratum of a call.
         *
         * @param methodId          the method
         * @param family            the family of the request, or null for a pooled verdict
         * @param capabilityVersion the version of the capability, or null for a pooled verdict
         * @return the stratum
         */
        public static Stratum of(String methodId, String family, String capabilityVersion) {
            return new Stratum(methodId, family == null ? "" : family,
                    capabilityVersion == null ? "" : capabilityVersion);
        }
    }

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
            for (MethodVerdict verdict : store.verdicts(workspaceId)) {
                verdicts.put(Stratum.of(verdict.methodId(), verdict.family(),
                        verdict.capabilityVersion()), verdict);
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
        record(methodId, nanos, succeeded
                ? MethodOutcome.UNVERIFIED : MethodOutcome.TECHNICAL_FAILURE);
    }

    /**
     * Records one measured call and the kind of outcome it had.
     *
     * <p><b>What this changes.</b> The boolean this replaces said only whether the call
     * completed. The kind says what the call <i>asserted</i>: a validated result, a wrong one,
     * a partial one, an unsupported one, an abstention, or no assertion at all. The mandate
     * asks for exactly this distinction — "a technical failure is not the same as an incorrect
     * inference, an abstention is not necessarily a failure" — and without it the profile
     * cannot tell a method that is right from a method that merely does not throw.</p>
     *
     * <p>The verdict is recorded <b>without a stratum</b>, and a pooled verdict is not
     * evidence for any particular request — the refusal to pool, made a first-class state
     * instead of an accident of the key.</p>
     *
     * @param methodId the method that was run
     * @param nanos    how long it took, in nanoseconds
     * @param outcome  what kind of outcome the call had
     */
    public void record(String methodId, long nanos, MethodOutcome outcome) {
        record(methodId, nanos, outcome, "", "");
    }

    /**
     * Records one measured call, its cost and its verdict, in the stratum it belongs to.
     *
     * @param methodId          the method that was run
     * @param nanos             how long it took, in nanoseconds
     * @param outcome           what kind of outcome the call had
     * @param family            the family of the request it answered
     * @param capabilityVersion the version of the capability that ran
     */
    public void record(String methodId, long nanos, MethodOutcome outcome,
                       String family, String capabilityVersion) {
        if (methodId == null || methodId.isBlank()) {
            throw new IllegalArgumentException("method id must not be blank");
        }
        if (nanos < 0) {
            throw new IllegalArgumentException("measured nanoseconds must not be negative");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("the outcome of a measurement must not be null");
        }
        measurements.compute(methodId, (id, existing) -> {
            // The counters say what happened to the call, not whether the method was
            // right: a call that returned is a completion, and a call that threw, ran
            // out of its budget, was refused or was cancelled is not. The accuracy
            // lives in the verdicts, which carry the whole taxonomy.
            boolean completed = outcome.completed();
            if (existing == null) {
                return new Measurement(id, nanos, 1, completed ? 1 : 0, completed ? 0 : 1);
            }
            return new Measurement(id,
                    existing.totalNanos() + nanos,
                    existing.calls() + 1,
                    existing.successes() + (completed ? 1 : 0),
                    existing.failures() + (completed ? 0 : 1));
        });
        String stratumFamily = family == null ? "" : family;
        String stratumVersion = capabilityVersion == null ? "" : capabilityVersion;
        verdicts.compute(Stratum.of(methodId, stratumFamily, stratumVersion),
                (stratum, existing) -> existing == null
                        ? new MethodVerdict(methodId, stratumFamily, stratumVersion,
                                0, 0, 0, 0, 0, 0, 0, 0).with(outcome)
                        : existing.with(outcome));
        if (store != null) {
            store.recordMeasurement(workspaceId, methodId, nanos,
                    outcome.completed(), timestamps.get());
            store.recordVerdict(workspaceId, methodId, family, capabilityVersion, outcome,
                    timestamps.get());
        }
    }

    /**
     * Records the verdict of a call whose cost was measured elsewhere.
     *
     * <p><b>Why this exists.</b> A verdict and a cost are two different facts, and a peer
     * supplies only the first: the peer measured its own execution with its own clock, and
     * that number is not comparable with the nanoseconds this process measures for its own
     * methods. Writing the peer's price into the measurement table would let two clocks
     * share one column, so the verdict is recorded here and the cost stays on the dispatch's
     * own record, where the peer's own stages are already written.</p>
     *
     * @param methodId          the method the verdict is about
     * @param outcome           the kind of outcome the call had
     * @param family            the family of the request it answered
     * @param capabilityVersion the version of the capability that ran
     */
    public void recordVerdict(String methodId, MethodOutcome outcome, String family,
                              String capabilityVersion) {
        if (methodId == null || methodId.isBlank()) {
            throw new IllegalArgumentException("method id must not be blank");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("the outcome of a verdict must not be null");
        }
        String stratumFamily = family == null ? "" : family;
        String stratumVersion = capabilityVersion == null ? "" : capabilityVersion;
        verdicts.compute(Stratum.of(methodId, stratumFamily, stratumVersion),
                (stratum, existing) -> existing == null
                        ? new MethodVerdict(methodId, stratumFamily, stratumVersion,
                                0, 0, 0, 0, 0, 0, 0, 0).with(outcome)
                        : existing.with(outcome));
        if (store != null) {
            store.recordVerdict(workspaceId, methodId, family, capabilityVersion, outcome,
                    timestamps.get());
        }
    }

    /**
     * The reliability of a method in a stratum, as an estimate with its uncertainty.
     *
     * <p>The estimate is computed over the trials in which the method <i>decided</i>, and the
     * state says whether the evidence bears it: {@link MethodReliability.State#ESTIMATED}
     * only when the interval is narrow enough to act on. A method whose verdicts are pooled
     * over the strata is {@link MethodReliability.State#NO_EVIDENCE} here, because a pooled
     * rate is a statement about the wrong quantity.</p>
     *
     * @param methodId          the method
     * @param family            the family of the request
     * @param capabilityVersion the version of the capability
     * @return the estimate, never null
     */
    public MethodReliability reliability(String methodId, String family, String capabilityVersion) {
        if (methodId == null) {
            return MethodReliability.neverMeasured(null);
        }
        MethodVerdict verdict = verdicts.get(Stratum.of(methodId, family, capabilityVersion));
        if (verdict == null) {
            // The observations may exist, but POOLED over the strata. A pooled rate is a
            // statement about the wrong quantity, and the refusal to pool is a first-class
            // state — not an accident of the key.
            MethodVerdict pooled = verdicts.get(Stratum.of(methodId, "", ""));
            if (pooled == null) {
                return MethodReliability.neverMeasured(methodId);
            }
            return MethodReliability.of(pooled);
        }
        return MethodReliability.of(verdict);
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

    /** Forgets every measurement and every verdict. */
    public void clear() {
        measurements.clear();
        verdicts.clear();
    }

    /**
     * Runs a body, records how long it took and what it asserted, and returns what it
     * produced.
     *
     * <p>This is the only method in the package that reads a clock, and it is the reason the
     * package can promise that a decision never depends on timing: the cost is attached to a
     * finished call, after the fact.</p>
     *
     * <p><b>Why this delegates.</b> The taxonomy of outcomes must have one
     * implementation: a path that recorded a refusal as a failure here and as an
     * abstention there would report two different histories for the same call. This
     * overload has no stratum of its own, so its verdict is recorded in the pooled
     * stratum — and a pooled rate is a statement about the wrong quantity, so the
     * estimate refuses it (see {@link #reliability}).</p>
     *
     * @param methodId the method being measured
     * @param body     the work
     * @param <T>      what the work produces
     * @return what the work produced
     */
    public <T> T measure(String methodId, Supplier<T> body) {
        return measure(methodId, "", "", body);
    }

    /**
     * Runs a body, records what it cost and what it asserted, and returns what it produced.
     *
     * <p><b>What the outcome is here.</b> The body returned, or it refused, or it threw —
     * and nothing confirmed that the answer was right, because nothing in this path is a
     * verifier. The verdict recorded is therefore {@link MethodOutcome#UNVERIFIED} for a call
     * that returned, {@link MethodOutcome#ABSTAINED} for a deliberate refusal and
     * {@link MethodOutcome#TECHNICAL_FAILURE} for a call that threw. A returned answer that
     * nobody verified is a <i>completion</i>, and a completion is not an accuracy.</p>
     *
     * @param methodId          the method being measured
     * @param family            the family of the request it answered
     * @param capabilityVersion the version of the capability that ran
     * @param body              the work
     * @param <T>               what the work produces
     * @return what the work produced
     */
    public <T> T measure(String methodId, String family, String capabilityVersion,
                         Supplier<T> body) {
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }
        long startedAt = System.nanoTime();
        MethodOutcome outcome = MethodOutcome.UNVERIFIED;
        try {
            T result = body.get();
            return result;
        } catch (AlgorithmRefusalException refusal) {
            outcome = MethodOutcome.ABSTAINED;
            throw refusal;
        } catch (RuntimeException | Error failure) {
            outcome = MethodOutcome.TECHNICAL_FAILURE;
            throw failure;
        } finally {
            record(methodId, System.nanoTime() - startedAt, outcome, family, capabilityVersion);
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
