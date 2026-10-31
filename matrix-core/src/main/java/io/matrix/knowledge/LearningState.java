package io.matrix.knowledge;

import java.util.List;

/**
 * The learning state of one concept in one dimension (owner directive §10).
 *
 * <p>The state is derived from recorded outcomes and never asserted. When
 * the evidence contradicts a state that was reached earlier, the state
 * FALLS and the transition is explained in the note - a regression must be
 * observable, not hidden behind a stale mastery label.
 */
public record LearningState(
        String conceptId,
        LearningDimension dimension,
        LearningStage stage,
        double score,
        int evidenceCount,
        int variantCount,
        int heldOutCount,
        int criticalFailures,
        List<String> evidenceItemIds,
        String note) {

    public LearningState {
        evidenceItemIds = List.copyOf(evidenceItemIds == null ? List.of() : evidenceItemIds);
    }

    /** The state of a concept and dimension that has never been assessed. */
    public static LearningState unknown(String conceptId, LearningDimension dimension) {
        return new LearningState(conceptId, dimension, LearningStage.UNKNOWN, 0.0,
                0, 0, 0, 0, List.of(),
                "No assessment has been recorded for this dimension.");
    }
}
