package io.matrix.knowledge.concept;

/**
 * One row of the CLAIM lineage.
 *
 * <p><b>The claim's id does not change.</b> A claim's id is a digest of the workspace,
 * the content hash and the proposition, and none of the three moves in the migration —
 * so the claims, the evidence and the proposition-based golds survive it. What moves is
 * the concept the claim points at, and this row says which concept it was and which it
 * is now.</p>
 *
 * @param claimId the claim — unchanged
 * @param oldConceptId the concept the claim pointed at
 * @param newConceptId the concept the claim points at now
 * @param reason why
 * @param createdAt when the row was written
 */
public record ClaimLineage(String claimId, String oldConceptId, String newConceptId,
                            String reason, String createdAt) {
}
