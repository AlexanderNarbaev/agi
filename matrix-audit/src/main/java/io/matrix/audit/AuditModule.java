package io.matrix.audit;

/**
 * WAVE T-01 placeholder — Wave T-06 will implement hash-chained audit logs.
 *
 * <p>Final design (T-06):</p>
 * <ul>
 *   <li>{@code HashChainedLog}: append-only, each entry hashes previous + payload (SHA-256).</li>
 *   <li>{@code GdprPruner}: selective memory pruning honoring "Right to be Forgotten".</li>
 *   <li>{@code ComplianceReporter}: PDF generation for SOX/HIPAA/GDPR audits.</li>
 * </ul>
 *
 * <p><b>CONSTITUTION compliance:</b> No LLM access. Logs are pure structural records.</p>
 */
public final class AuditModule {

    /** Module version. */
    public static final String VERSION = "0.1.0-T01";

    /** SHA-256 algorithm — used by the hash chain in T-06. */
    public static final String HASH_ALGORITHM = "SHA-256";

    private AuditModule() {
        // static facade only
    }

    /**
     * Liveness probe used by health endpoints.
     */
    public static String status() {
        return "matrix-audit:" + VERSION + ":hash=" + HASH_ALGORITHM;
    }
}
