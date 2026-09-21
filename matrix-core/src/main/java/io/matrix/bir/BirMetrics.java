package io.matrix.bir;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Minimal static metrics core for BIR forms (SPEC-002 INV-2).
 *
 * <p>No-op until {@link #attach(MeterRegistry)} is called once by the
 * observability layer. Keeps {@code bir/} free of CDI/Quarkus wiring;
 * metrics are observational only and never affect computation
 * (runtime determinism preserved).
 */
public final class BirMetrics {

    private static volatile MeterRegistry registry;

    private BirMetrics() {}

    /** Attach a MeterRegistry (called once from observability bootstrap). */
    public static void attach(MeterRegistry meterRegistry) {
        registry = meterRegistry;
    }

    /** Detach the registry (testing only). */
    public static void detach() {
        registry = null;
    }

    /**
     * Record creation of a CLAUSESET form: one counter tick plus the
     * clause count and total literal count as distribution summaries.
     */
    public static void recordClauseSetCreated(int clauses, long literals) {
        MeterRegistry r = registry;
        if (r == null) return;
        r.counter("matrix.bir.clauseset.created").increment();
        r.summary("matrix.bir.clauseset.clauses").record(clauses);
        r.summary("matrix.bir.clauseset.literals").record(literals);
    }
}
