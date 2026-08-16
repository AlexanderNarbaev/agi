package io.matrix.privacy;

import io.matrix.privacy.storage.TombstoneStorage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central GDPR service — single entry point for privacy operations
 * (tombstones, cascade erasure, audit, export).
 *
 * <p>Wraps the underlying {@link TombstoneService} with:
 * <ul>
 *   <li>standard reason codes ({@link Tombstone#REASON_GDPR_ERASURE}, etc.)</li>
 *   <li>auto-generated signatures (SHA-256 of resourceId+timestamp+reason)</li>
 *   <li>cascade erasure (delegates to {@link CascadeTombstoneService})</li>
 *   <li>export functionality (formatted audit log dump)</li>
 *   <li>counters (totalErasures, totalCascades) for monitoring</li>
 * </ul>
 *
 * <p>Ref: L6 §6.7 (GDPR), L12 §4 (legal framework).
 */
public final class PrivacyService {

    private final TombstoneService tombstones;
    private final CascadeTombstoneService cascade;
    private final AtomicLong totalErasures = new AtomicLong();
    private final AtomicLong totalCascades = new AtomicLong();

    /** Constructor with cascade service (production). */
    public PrivacyService(TombstoneService tombstones, CascadeTombstoneService cascade) {
        this.tombstones = Objects.requireNonNull(tombstones, "tombstones");
        this.cascade = Objects.requireNonNull(cascade, "cascade");
    }

    /** Constructor without cascade (simple use cases). */
    public PrivacyService(TombstoneService tombstones) {
        this(tombstones, new CascadeTombstoneService(tombstones));
    }

    // ── Erasure API ──

    /** Record a GDPR Article 17 erasure. */
    public Tombstone eraseGdpr(String subjectId, String resourceType, String resourceId) {
        return erase(Tombstone.REASON_GDPR_ERASURE, subjectId, resourceType, resourceId);
    }

    /** Record a GDPR subject-request erasure. */
    public Tombstone eraseSubjectRequest(String subjectId, String resourceType, String resourceId) {
        return erase(Tombstone.REASON_DATA_SUBJECT_REQUEST, subjectId, resourceType, resourceId);
    }

    /** Record a legal hold. */
    public Tombstone legalHold(String subjectId, String resourceType, String resourceId) {
        return erase(Tombstone.REASON_LEGAL_HOLD, subjectId, resourceType, resourceId);
    }

    /** Record an operational cleanup. */
    public Tombstone operationalCleanup(String subjectId, String resourceType, String resourceId) {
        return erase(Tombstone.REASON_OPERATIONAL, subjectId, resourceType, resourceId);
    }

    /** Record an erasure with a custom reason (no cascade). */
    public Tombstone erase(String reason, String subjectId, String resourceType,
                           String resourceId) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        totalErasures.incrementAndGet();
        String sig = signature(resourceId, reason);
        return tombstones.tombstone(subjectId, resourceType, resourceId, reason, sig, "PrivacyService");
    }

    /** Record an erasure with cascade (deletes all dependent resources). */
    public List<Tombstone> eraseAndCascade(String reason, String subjectId,
                                            String sourceType, String sourceId) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceId, "sourceId");
        List<Tombstone> result = cascade.tombstoneAndCascade(
                subjectId, sourceType, sourceId, reason, "PrivacyService");
        totalErasures.incrementAndGet();
        totalCascades.addAndGet(cascade.cascadeCount());
        return result;
    }

    /** GDPR Article 17 erasure with cascade. */
    public List<Tombstone> eraseGdprAndCascade(String subjectId, String sourceType, String sourceId) {
        return eraseAndCascade(Tombstone.REASON_GDPR_ERASURE, subjectId, sourceType, sourceId);
    }

    // ── Query API ──

    public Optional<Tombstone> find(String resourceType, String resourceId) {
        return Optional.ofNullable(tombstones.find(resourceType, resourceId));
    }

    public List<Tombstone> all() {
        return tombstones.all();
    }

    public List<Tombstone> findByReason(String reasonPrefix) {
        return tombstones.filterByReason(reasonPrefix);
    }

    public List<Tombstone> findBySubject(String subjectId) {
        return tombstones.filterBySubject(subjectId);
    }

    public int count() {
        return tombstones.count();
    }

    // ── Export API ──

    /**
     * Export the full audit log as a human-readable multi-line text dump.
     * Suitable for compliance reports, regulatory filings, log aggregation.
     */
    public String exportAuditLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("MATRIX Privacy Audit Log\n");
        sb.append("Generated: ").append(java.time.Instant.now()).append('\n');
        sb.append("Total tombstones: ").append(tombstones.count()).append('\n');
        sb.append("Total erasures (lifetime): ").append(totalErasures.get()).append('\n');
        sb.append("Total cascades (lifetime): ").append(totalCascades.get()).append('\n');
        sb.append("---\n");
        for (Tombstone t : tombstones.all()) {
            sb.append(t.id()).append(' ')
                    .append(t.deletedAt()).append(' ')
                    .append(t.subjectId()).append(' ')
                    .append(t.resourceType()).append('/').append(t.resourceId()).append(' ')
                    .append(t.reason()).append('\n');
        }
        return sb.toString();
    }

    /** Compact JSON export (one record per line). */
    public String exportJsonLines() {
        StringBuilder sb = new StringBuilder();
        for (Tombstone t : tombstones.all()) {
            sb.append('{')
                    .append("\"id\":\"").append(t.id()).append("\",")
                    .append("\"subjectId\":\"").append(t.subjectId()).append("\",")
                    .append("\"resourceType\":\"").append(t.resourceType()).append("\",")
                    .append("\"resourceId\":\"").append(t.resourceId()).append("\",")
                    .append("\"reason\":\"").append(t.reason()).append("\",")
                    .append("\"deletedAt\":\"").append(t.deletedAt()).append("\",")
                    .append("\"requesterId\":\"").append(t.requesterId()).append("\"")
                    .append("}\n");
        }
        return sb.toString();
    }

    // ── Counters ──

    public long totalErasures() { return totalErasures.get(); }
    public long totalCascades() { return totalCascades.get(); }
    public TombstoneService tombstones() { return tombstones; }
    public CascadeTombstoneService cascade() { return cascade; }

    /** Stable signature (SHA-256 hex of resourceId + reason + timestamp). */
    static String signature(String resourceId, String reason) {
        try {
            byte[] payload = (resourceId + "|" + reason + "|" + System.nanoTime())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }
}