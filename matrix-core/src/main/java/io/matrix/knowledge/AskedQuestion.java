package io.matrix.knowledge;

import java.util.List;

/**
 * A question the workspace was asked and its own record of the asking
 * (owner directive §19 Workflow C, §20 MemoryConsolidator).
 *
 * <p>This is the workbench's episodic log, reduced to what a learning
 * application needs: the question, the terms of it that nothing in the
 * workspace covered, and whether the workspace can answer it NOW.
 *
 * <p>The questions are grouped by a CANONICAL KEY, so that two different
 * wordings of the same question collapse onto one record. The key is the
 * sorted set of the question's folded content terms - not a hash, so that
 * "what is photosynthesis" and "photosynthesis, please" are visibly the same
 * question and "what is photolysis" is visibly a different one.
 *
 * <p>The record also says whether the workspace ANSWERED the question when
 * it was last asked, so that the refusal rate can be reported rather than
 * guessed.
 *
 * <p>A question that the workspace now answers is RESOLVED, not deleted.
 * That is the same rule the rest of the knowledge model follows, and the
 * same rule the MIND-W3 branch applied to its episodic store: a record that
 * a question was once asked and once could not be answered is evidence of
 * the gap, and the gap does not stop existing because it has been closed.
 */
public record AskedQuestion(
        String id,
        String workspaceId,
        String canonicalKey,
        String question,
        List<String> uncoveredTerms,
        int timesAsked,
        boolean answered,
        String firstAskedAt,
        String lastAskedAt,
        Status status,
        String resolutionNote) {

    /** Whether the workspace can answer the question. */
    public enum Status {
        /** The workspace could not answer it when it was last asked. */
        OPEN,
        /** The workspace can answer it now. The record of the gap is kept. */
        RESOLVED
    }

    public AskedQuestion {
        uncoveredTerms = List.copyOf(uncoveredTerms == null ? List.of() : uncoveredTerms);
        if (status == null) {
            throw new IllegalArgumentException("an asked question needs a status");
        }
    }
}
