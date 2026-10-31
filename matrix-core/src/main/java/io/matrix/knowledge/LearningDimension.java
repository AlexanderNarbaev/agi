package io.matrix.knowledge;

/**
 * The separate dimensions of learning (owner directive §10).
 *
 * <p>A concept may have strong factual recall and poor causal understanding;
 * another may be explainable and not yet applicable; a skill may be
 * operationally competent while the theory behind it stays incomplete. One
 * scalar score cannot hold any of that, so it is not used: the state of a
 * concept is a vector over these dimensions, and each element is derived
 * from recorded assessment outcomes rather than asserted.
 */
public enum LearningDimension {

    /** Can the learner recall the factual content. */
    RECALL("Factual recall"),

    /** Can the learner explain what something means in its own terms. */
    EXPLANATION("Conceptual explanation"),

    /** Can the learner carry out a defined procedure reliably. */
    PROCEDURE("Procedural skill"),

    /** Can the learner reason about causes and predict what follows. */
    CAUSAL("Causal understanding"),

    /** Can the learner apply the knowledge to a task. */
    APPLICATION("Application"),

    /** Can the learner apply the knowledge in a setting it was not built for. */
    TRANSFER("Novel-context transfer"),

    /** Can the learner judge the quality of an explanation or a claim. */
    EVALUATION("Evaluation and critique"),

    /** Can the learner combine the knowledge into something new. */
    SYNTHESIS("Creative synthesis"),

    /** Can the learner teach the capability to another learner. */
    TEACHING("Teaching capability"),

    /** Does the learner's confidence match its correctness. */
    CALIBRATION("Confidence calibration"),

    /** Does the capability survive an interval or a restart. */
    RETENTION("Retention over time"),

    /** Is the learner's answer traceable to the sources it rests on. */
    GROUNDING("Source-grounded accuracy");

    private final String label;

    LearningDimension(String label) {
        this.label = label;
    }

    /** The human-readable name of the dimension. */
    public String label() {
        return label;
    }
}
