package io.matrix.knowledge;

/**
 * EPISTEMIC-INTEGRITY P1 — a concept's definition, with the span it was read from.
 *
 * <p>The concept layer had 348 claims, 326 concepts and <b>0 usable definitions</b>:
 * the importer wrote every concept with {@code definition = null}. This record
 * is what the acquisition path now writes: the qualifying sentence of the
 * concept's own source document, <b>verbatim</b>, together with the place it was
 * read from.</p>
 *
 * <p><b>WHY A DEFINITION IS ITS OWN RECORD and not a column of the concept.</b>
 * The definition is a <b>span</b>: the sentence, the source it came from, the
 * characters it covers in that source's extracted text, the claim that carries
 * it and the rule that produced it. The concept table's {@code definition}
 * column holds a bare text and nothing else, and the column is read by the
 * retriever, which appends it to every claim's searchable text
 * ({@code KnowledgeRetriever.java:170-172}). Filling that column therefore
 * changes the retrieval, and the measurement of this wave found that it
 * changes it for the worse: the official evaluation moves from 18/29 to 17/29
 * and the unanswerable abstention from 6/6 to 5/6, because the wider searchable
 * text lets an unsupported question pass the floor. That is the retrieval
 * variant H1 of the candidate freeze, and it is a separate decision. This
 * record keeps the definition <b>and its provenance</b> without changing a
 * single retrieval.</p>
 *
 * <p><b>WHAT IT IS NOT.</b> It is not a change of identity: the concept's id,
 * its name and every claim, every evidence record and every proposition-based
 * gold are untouched.</p>
 *
 * @param workspaceId the workspace
 * @param conceptId the concept the definition belongs to
 * @param definition the definition — the qualifying sentence, verbatim
 * @param sourceId the source the sentence was read from
 * @param startOffset where the sentence starts in the source's extracted text
 * @param endOffset where the sentence ends in the source's extracted text
 * @param claimId the claim that carries the sentence
 * @param extractionMethod the name of the rule that produced the definition
 * @param recordedAt when the definition was recorded
 */
public record ConceptDefinition(
        String workspaceId,
        String conceptId,
        String definition,
        String sourceId,
        int startOffset,
        int endOffset,
        String claimId,
        String extractionMethod,
        String recordedAt) {

    public ConceptDefinition {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspace id must not be blank");
        }
        if (conceptId == null || conceptId.isBlank()) {
            throw new IllegalArgumentException("concept id must not be blank");
        }
        if (definition == null || definition.isBlank()) {
            throw new IllegalArgumentException("a definition must not be blank");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("the source of a definition must not be blank");
        }
        if (extractionMethod == null || extractionMethod.isBlank()) {
            throw new IllegalArgumentException("the extraction method must not be blank");
        }
    }
}
