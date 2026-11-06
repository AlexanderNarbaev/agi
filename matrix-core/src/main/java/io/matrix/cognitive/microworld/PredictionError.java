package io.matrix.cognitive.microworld;

/**
 * W44.4 — a prediction the world contradicted.
 *
 * <p>The micro-world's counterpart of {@link io.matrix.cognitive.ErrorSignal}: the session
 * predicted a value for an assignment, the world answered differently, and the difference is
 * recorded with the hypothesis that is responsible for it.</p>
 *
 * <h2>Who is responsible</h2>
 *
 * <p>When the prediction was unanimous, every surviving hypothesis made it, and the record names
 * the one with the smallest identifier: the session does not single out a culprit it cannot
 * distinguish. The {@code note} names all of them. When the prediction was NOT unanimous, the
 * session never made a definite claim, and the note says so — an ambiguity that was visible
 * before the outcome is not a mistake the session made.</p>
 *
 * <h2>What an error does NOT do</h2>
 *
 * <p>An error does not rewrite the model. The session records it, flags the implicated
 * hypotheses for review, and leaves the hypothesis set standing: it cannot tell whether the world
 * or its own model is wrong, and rewriting the model on a datum whose cause is unresolved would
 * destroy the evidence of the disagreement.</p>
 */
public record PredictionError(String id, WorldInput input, boolean predicted, boolean observed,
                               String responsibleHypothesisId, String note) {

    public PredictionError {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("an error id must not be blank");
        }
        if (input == null) {
            throw new IllegalArgumentException("an error must name the assignment it is about");
        }
        if (responsibleHypothesisId == null || responsibleHypothesisId.isBlank()) {
            throw new IllegalArgumentException("an error must name the hypothesis responsible for it");
        }
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("an error must say what happened");
        }
    }

    /** The canonical key of the assignment the error is about. */
    public String assignmentKey() {
        return input.key();
    }

    @Override
    public String toString() {
        return "PredictionError[" + id + " " + input.key() + " predicted=" + predicted
                + " observed=" + observed + " responsible=" + responsibleHypothesisId + "]";
    }
}
