package io.matrix.privacy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Marker record indicating that a piece of knowledge has been deleted under
 * GDPR Article 17 ("right to erasure") or any other lawful-removal request.
 *
 * <p>The tombstone intentionally preserves:
 * <ul>
 *   <li>the identity of the {@code subjectId} (e.g. data subject ID)</li>
 *   <li>the {@code deletedAt} timestamp</li>
 *   <li>the {@code reason} for deletion (legal/compliance/operational)</li>
 *   <li>an immutable {@code signature} — content hash of the original entry, so
 *       later audits can prove "this entry WAS present, and was erased".</li>
 * </ul>
 *
 * <p>The tombstone does NOT preserve any of the deleted payload. It is safe
 * to store in long-term audit logs.
 *
 * <p>Ref: L6 §6.7 (GDPR tombstoning), L12 §4 (legal framework).
 */
public record Tombstone(
        UUID id,
        String subjectId,
        String resourceType,     // "FnlPackage" | "Neuron" | "Snapshot" | ...
        String resourceId,
        String reason,
        String signature,
        Instant deletedAt,
        String requesterId) {

    public Tombstone {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(deletedAt, "deletedAt");
        Objects.requireNonNull(requesterId, "requesterId");
        if (signature == null) signature = "";  // tolerate missing signatures
    }

    /** Standard "GDPR Article 17 erasure" reason. */
    public static final String REASON_GDPR_ERASURE = "gdpr.erasure";
    public static final String REASON_DATA_SUBJECT_REQUEST = "gdpr.subject_request";
    public static final String REASON_LEGAL_HOLD = "legal.hold";
    public static final String REASON_OPERATIONAL = "operational.cleanup";

    public boolean isGdprErasure() {
        return reason != null && reason.startsWith("gdpr.");
    }
}
