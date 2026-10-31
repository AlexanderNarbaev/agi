package io.matrix.knowledge;

/**
 * A verifiable relationship between a source and a claim
 * (owner directive §14).
 *
 * <p>Evidence is what a claim's status rests on. A claim with no evidence
 * link can only be DRAFT, and the store enforces that: promotion to
 * SUPPORTED, VERIFIED, CONTESTED or REFUTED requires a link.
 */
public record Evidence(
        String id,
        String workspaceId,
        String claimId,
        String sourceId,
        EvidenceRelation relation,
        String excerpt,
        int quality,
        String recordedAt) {

    public Evidence {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("evidence id must not be blank");
        }
        if (claimId == null || claimId.isBlank()) {
            throw new IllegalArgumentException("evidence claim id must not be blank");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("evidence source id must not be blank");
        }
        if (relation == null) {
            throw new IllegalArgumentException("evidence relation must not be null");
        }
        quality = Math.max(0, Math.min(100, quality));
    }
}
