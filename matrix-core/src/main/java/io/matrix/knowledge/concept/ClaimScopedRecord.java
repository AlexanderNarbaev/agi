package io.matrix.knowledge.concept;

/**
 * One CLAIM-SCOPED record: a claim that has no referent, on its own.
 *
 * <p>The owner's decision OD-061: a claim with no concept keeps its own text as the
 * retrievable unit through an <b>additive</b> structure, and {@code concept_id} in the
 * core table stays {@code NOT NULL}. The record is therefore a concept row (so the
 * claim can point at it) plus this row, which says what the record IS: not a referent
 * of the corpus, but the claim's own text, kept so that nothing is lost.</p>
 *
 * @param conceptId the id of the concept row the claim points at
 * @param workspaceId the workspace
 * @param claimId the claim the record carries
 * @param text the claim's own text — the retrievable unit
 * @param reason why the claim has no referent
 * @param createdAt when the record was written
 */
public record ClaimScopedRecord(String conceptId, String workspaceId, String claimId,
                                String text, String reason, String createdAt) {
}
