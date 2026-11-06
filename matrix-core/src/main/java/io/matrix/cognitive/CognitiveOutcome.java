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
        List<String> notes) {

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

    /** True when the cycle found a prediction error. */
    public boolean detectedError() {
        return error != null;
    }

    @Override
    public String toString() {
        return "CognitiveOutcome[" + episodeId + " " + status + " answer=" + answer + "]";
    }
}
