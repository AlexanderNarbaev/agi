package io.matrix.knowledge;

import java.util.List;

/**
 * One recorded transition of a capability's state.
 *
 * <p>RECON-W42.1. The mission's §7 requires the progressive knowledge model
 * to be a **real state machine**: "The lifecycle is revisable. A previously
 * mastered concept may return to PRACTICE when new evidence contradicts an
 * existing model… Preserve earlier mastery records as historical evidence.
 * Do not erase past failures."
 *
 * <p>RECON-W41 computed a stage from the outcome log and threw the history
 * away — there was nowhere to put it, and a recomputation cannot say *when*
 * a state changed or *why*. This record is that somewhere.
 *
 * <p><b>What a transition is not.</b> It is not a claim about the learner.
 * It is a record that the **evidence** changed the system's reading of the
 * evidence, with the outcome that caused it named. A transition with no
 * cause is not a transition; it is a re-labelling.
 */
public record LearningTransition(
        String id,
        String workspaceId,
        String conceptId,
        LearningDimension dimension,
        LearningStage fromStage,
        LearningStage toStage,
        String reason,
        String outcomeId,
        String occurredAt) {

    public LearningTransition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a transition must have an id");
        }
        if (toStage == null) {
            throw new IllegalArgumentException("a transition must have a target stage");
        }
    }

    /**
     * Whether this transition moved the capability backwards.
     *
     * <p>A regression is not a failure of the system: it is the system
     * noticing that its model no longer fits the evidence. The mission says
     * the regression "must be observable and explained" — this is the
     * observable half, and {@link #reason()} is the explained half.
     */
    public boolean regression() {
        return fromStage != null && toStage.index() < fromStage.index();
    }

    /** A one-line description, for the UI and the reports. */
    public String describe() {
        String from = fromStage == null ? "nothing" : fromStage.name();
        String movement = regression() ? "REGRESSED" : "advanced";
        return movement + ": " + from + " → " + toStage.name()
                + (reason == null || reason.isBlank() ? "" : " (" + reason + ")");
    }

    /** The transitions of a workspace, oldest first. */
    public static List<LearningTransition> none() {
        return List.of();
    }
}
