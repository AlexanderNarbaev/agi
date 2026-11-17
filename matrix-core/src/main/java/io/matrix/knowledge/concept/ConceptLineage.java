package io.matrix.knowledge.concept;

/**
 * One row of the CONCEPT lineage: what superseded what, and why.
 *
 * <p>The lineage is <b>append-only</b> and the old rows are never removed: a concept
 * that the migration superseded stays in the store, marked by the fact that its id
 * appears here as an {@code oldId}.</p>
 *
 * @param oldId the id that was superseded
 * @param newId the id that superseded it
 * @param reason why
 * @param createdAt when the row was written
 */
public record ConceptLineage(String oldId, String newId, String reason, String createdAt) {
}
