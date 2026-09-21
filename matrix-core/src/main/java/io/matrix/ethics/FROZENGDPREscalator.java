package io.matrix.ethics;

import io.matrix.audit.HashLink;
import io.matrix.privacy.Tombstone;
import io.matrix.privacy.TombstoneService;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Bridges {@link FROZENFNLGuardian} (the ethics layer) with
 * {@link TombstoneService} (the GDPR audit layer). Every REJECT verdict
 * is automatically recorded as a tombstone — a permanent, tamper-evident
 * record that "subject X was denied the action Y at time T for reason Z".
 *
 * <p>Why this matters:
 * <ul>
 *   <li>Regulators (GDPR Article 30) require a record of every denied
 *       action, not just every successful one. This class provides that
 *       automatically — no application code can forget to log a denial.</li>
 *   <li>Each tombstone carries a {@code subjectId} (the offending action
 *       text or actor id) and a {@code reason} derived from the violated
 *       axiom. Audit consumers can replay decisions from the
 *       {@link FROZENFNLGuardian#chain()} and confirm what was denied.</li>
 *   <li>The same FROZEN FNL that produced the verdict is the one whose
 *       content is attested in the hash chain. Any retroactive
 *       modification of the FROZEN network is detectable via
 *       {@link io.matrix.audit.HashChain#verify()}.</li>
 * </ul>
 *
 * <p>Ref: L7 §5 (FROZEN), L6 §6.7 (GDPR), L12 §4 (legal audit).
 */
public final class FROZENGDPREscalator {

    private final FROZENFNLGuardian guardian;
    private final TombstoneService tombstones;
    private final Supplier<String> subjectIdSupplier;
    private final AtomicLong escalationsTriggered = new AtomicLong();

    public FROZENGDPREscalator(FROZENFNLGuardian guardian, TombstoneService tombstones) {
        this(guardian, tombstones, () -> "subject-anonymous");
    }

    public FROZENGDPREscalator(FROZENFNLGuardian guardian, TombstoneService tombstones,
                                Supplier<String> subjectIdSupplier) {
        this.guardian = Objects.requireNonNull(guardian, "guardian");
        this.tombstones = Objects.requireNonNull(tombstones, "tombstones");
        this.subjectIdSupplier = Objects.requireNonNull(subjectIdSupplier, "subjectIdSupplier");
    }

    /**
     * Evaluate {@code action} through the FROZEN FNL. When the verdict is
     * REJECT, a tombstone is automatically recorded.
     *
     * @return the {@link EthicalVerdict} (APPROVED or REJECTED)
     */
    public EthicalVerdict evaluateAndRecord(String action) {
        Objects.requireNonNull(action, "action");
        EthicalVerdict verdict = wrapEvaluation(action);
        if (verdict == EthicalVerdict.REJECTED) {
            recordTombstone(action);
        }
        return verdict;
    }

    /**
     * Evaluate {@code action} AND record the verdict in the chain —
     * even when APPROVED. Useful for full auditability.
     */
    public EthicalVerdict evaluateAndRecordAlways(String action) {
        Objects.requireNonNull(action, "action");
        EthicalVerdict verdict = wrapEvaluation(action);
        recordTombstone(action);  // tombstone for both APPROVED and REJECTED
        return verdict;
    }

    /**
     * Manually record a tombstone for an arbitrary resource — useful for
     * actions that need to be deleted post-hoc, not just denied upfront.
     */
    public Tombstone tombstoneResource(String subjectId, String resourceType,
                                        String resourceId, String reason) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(reason, "reason");
        escalationsTriggered.incrementAndGet();
        return tombstones.tombstone(subjectId, resourceType, resourceId, reason,
                "frozen-fnl-v1.0", "FROZENGDPREscalator");
    }

    public long escalationsTriggered() {
        return escalationsTriggered.get();
    }

    public FROZENFNLGuardian guardian() { return guardian; }
    public TombstoneService tombstones() { return tombstones; }

    // ── Internal ──

    private EthicalVerdict wrapEvaluation(String action) {
        // Use the FROZENFNLGuardian directly (which writes to the chain).
        // The guardian.evaluate call already appends a decision link.
        EthicalVerdict verdict = guardian.evaluate(action);
        return verdict;
    }

    private void recordTombstone(String action) {
        EthicalFilter.Axiom axiom = EthicalFilter.FROZEN_FNL.evaluateText(action).violatedAxiom();
        String reason = axiom == null
                ? "frozen.rejected"
                : "frozen.axiom." + axiom.name().toLowerCase();
        tombstones.tombstone(subjectIdSupplier.get(), "Action", hashAction(action),
                reason, "frozen-fnl-v1.0", "FROZENGDPREscalator");
        escalationsTriggered.incrementAndGet();
    }

    /** Hashes the action text for stable, deduplicated storage. */
    private static String hashAction(String action) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(action.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(action.hashCode());
        }
    }
}
