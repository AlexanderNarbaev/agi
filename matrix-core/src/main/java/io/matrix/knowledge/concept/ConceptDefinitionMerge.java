package io.matrix.knowledge.concept;

/**
 * One MERGED definition: a definition whose concept was collapsed into a referent
 * that already carried one.
 *
 * <p>The definitions live in their own table, keyed by (workspace, concept id), and the
 * migration collapses many concepts into one referent — so two definitions can land on
 * the same concept id, and the table's primary key refuses that. The first definition
 * wins and the merged one is recorded here, so that <b>nothing is lost</b>.</p>
 *
 * @param newConceptId the referent that now carries the definition
 * @param oldConceptId the concept the definition came from
 * @param definition the definition itself
 * @param reason why it was recorded here
 * @param createdAt when the row was written
 */
public record ConceptDefinitionMerge(String newConceptId, String oldConceptId, String definition,
                                      String reason, String createdAt) {
}
