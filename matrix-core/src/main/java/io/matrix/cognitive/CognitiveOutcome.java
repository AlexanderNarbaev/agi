package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmDecision;

import java.util.List;

/**
 * W44 — what one cognitive cycle produced.
 *
 * <p>The record carries the whole outcome of a cycle, including the parts that did NOT happen:
 * a cycle that refused says so, and a cycle that found a prediction error says so. The absence of
 * a field is not evidence of absence of an event — {@link #notes()} carries the things that
 * happened but have no field of their own.</p>
 *
 * <p>{@code status} is deliberately coarse. The fine-grained truth is in the trace, and the
 * trace is append-only, so a reader can see the order in which the system changed its mind.</p>
 */
public record CognitiveOutcome(
        String episodeId,
        Status status,
        String answer,
        Percept percept,
        Prediction prediction,
        ErrorSignal error,
        AlgorithmDecision decision,
        LearningTrace trace,
        List<String> notes,
        io.matrix.contract.Reflection reflection) {

    /** How the cycle ended. */
    public enum Status {
        /** The cycle ran and produced an answer. */
        COMPLETED,
        /** The input could not be understood, or nothing was permitted to act on it. */
        REFUSED,
        /** The cycle ran out of budget. */
        RESOURCE_LIMITED,
        /** The cycle failed. */
        FAILED
    }

    public CognitiveOutcome {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /**
     * The outcome of a cycle that produced no reflection.
     *
     * <p>W51. The reflection was added to the outcome in the middle of the wave, and the
     * callers that construct an outcome for a refusal or an interruption have no reflection
     * to hand: a cycle that never got to the end did not evaluate itself.</p>
     *
     * @param episodeId  the episode
     * @param status     how the cycle ended
     * @param answer     what the cycle answered
     * @param percept    what was perceived
     * @param prediction what was predicted
     * @param error      the comparison of the prediction with the outcome
     * @param decision   the method decision
     * @param trace      the trace of the cycle
     * @param notes      the notes of the cycle
     */
    public CognitiveOutcome(String episodeId, Status status, String answer, Percept percept,
                            Prediction prediction, ErrorSignal error, AlgorithmDecision decision,
                            LearningTrace trace, List<String> notes) {
        this(episodeId, status, answer, percept, prediction, error, decision, trace, notes, null);
    }

    /** True when the cycle found a prediction error. */
    public boolean detectedError() {
        return error != null;
    }

    @Override
    public String toString() {
        return "CognitiveOutcome[" + episodeId + " " + status + " answer=" + answer + "]";
    }
}
