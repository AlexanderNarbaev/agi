package io.matrix.audit;

/**
 * WAVE T-06 — Audit Module facade.
 *
 * The T-01 placeholder is replaced by the full implementation. The
 * underlying storage is {@link HashChainedLog}; this class exposes
 * the high-level API used by matrix-api-gateway.
 *
 * <p><b>CONSTITUTION compliance:</b> All access goes through
 * HashChainedLog — immutable, append-only, hash-chained. No LLM.</p>
 */
public final class AuditModule {

    /** Module version. */
    public static final String VERSION = "0.1.0-T06";

    /** SHA-256 algorithm — used by the hash chain. */
    public static final String HASH_ALGORITHM = "SHA-256";

    /** Singleton instance for convenience (T-06.5 will switch to DI). */
    private static final HashChainedLog SHARED_LOG = new HashChainedLog();

    /** Default GDPR pruner and compliance reporter. */
    private static final GdprPruner PRUNER = new GdprPruner(SHARED_LOG);
    private static final ComplianceReporter REPORTER = new ComplianceReporter(SHARED_LOG);
    private static final AnomalyDetector DETECTOR = new AnomalyDetector(SHARED_LOG);

    private AuditModule() {}

    /** Liveness probe. */
    public static String status() {
        return "matrix-audit:" + VERSION
            + ":hash=" + HASH_ALGORITHM
            + ":events=" + SHARED_LOG.size();
    }

    /** Append an event. Used by matrix-api-gateway AuditResource. */
    public static AuditEvent append(AuditEvent.Builder builder) {
        return SHARED_LOG.append(builder);
    }

    /** Verify chain integrity. Returns null if intact. */
    public static Integer verify() {
        return SHARED_LOG.verify();
    }

    /** GDPR pruner. */
    public static GdprPruner pruner() { return PRUNER; }

    /** Compliance reporter. */
    public static ComplianceReporter reporter() { return REPORTER; }

    /** Anomaly detector. */
    public static AnomalyDetector detector() { return DETECTOR; }

    /** Direct access for testing — returns the shared log. */
    public static HashChainedLog log() { return SHARED_LOG; }
}
