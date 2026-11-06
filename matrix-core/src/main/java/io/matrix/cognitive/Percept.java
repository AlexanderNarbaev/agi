package io.matrix.cognitive;

import java.util.List;

/**
 * W44.1 — a candidate interpretation of a {@link CognitiveSignal}.
 *
 * <p>A percept is an INTERPRETATION, never an observation. The record keeps the candidate
 * interpretations separate from the signal, so the trace can show that the system considered
 * more than one reading and can say which one it chose and why. A signal that could not be
 * interpreted at all produces a percept with {@code malformed = true} and an empty candidate
 * list — the system then refuses rather than guesses.</p>
 *
 * <p>{@code confidence} here is a self-reported quality of the interpretation, NOT evidence. It
 * is never allowed to raise the status of a claim (CONSTITUTION Article VI forbids claiming
 * reliability without measurement); it only ranks candidates inside one perception step.</p>
 *
 * <p>Deliberate non-goal: this record does not claim the interpretation is correct. The
 * {@code alternativeCount} and {@code notes} fields exist so a downstream reviewer can see that
 * an ambiguity was noticed rather than resolved by accident.</p>
 */
public record Percept(
        String signalId,
        CognitiveSignal.Channel channel,
        String interpretation,
        double confidence,
        boolean malformed,
        List<String> alternatives,
        List<String> notes) {

    public Percept {
        if (signalId == null || signalId.isBlank()) {
            throw new IllegalArgumentException("percept signal id must not be blank");
        }
        if (channel == null) {
            throw new IllegalArgumentException("percept channel must not be null");
        }
        if (confidence < 0.0 || confidence > 1.0 || Double.isNaN(confidence)) {
            throw new IllegalArgumentException("percept confidence must be within [0,1]");
        }
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** A well-formed percept with one reading. */
    public static Percept of(String signalId, CognitiveSignal.Channel channel,
                             String interpretation, double confidence) {
        return new Percept(signalId, channel, interpretation, confidence, false, List.of(), List.of());
    }

    /** A percept that records a failure to interpret the signal. */
    public static Percept malformed(String signalId, CognitiveSignal.Channel channel, String reason) {
        return new Percept(signalId, channel, "", 0.0, true, List.of(), List.of(reason));
    }

    /** True when the signal could not be understood. */
    public boolean unusable() {
        return malformed;
    }

    @Override
    public String toString() {
        return "Percept[" + signalId + " channel=" + channel + " malformed=" + malformed
                + " confidence=" + confidence + " alternatives=" + alternatives.size() + "]";
    }
}
