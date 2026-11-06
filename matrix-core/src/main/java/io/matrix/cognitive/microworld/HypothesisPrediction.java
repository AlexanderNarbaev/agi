package io.matrix.cognitive.microworld;

import java.util.List;

/**
 * W44.4 — what the session believes about an assignment it has NOT observed.
 *
 * <p>This is the record that keeps a prediction distinguishable from an observation. A prediction
 * is a temporal expectation: it was made BEFORE the outcome was known, and it is about an
 * assignment the session has not seen the answer to. The W44 kernel stores its predictions in
 * {@link io.matrix.cognitive.Prediction}; this record is the micro-world's own, narrower view of
 * the same fact.</p>
 *
 * <h2>What {@code value()} means when the hypotheses disagree</h2>
 *
 * <p><b>It is not a decision.</b> The session does not resolve an ambiguity silently: when the
 * surviving hypotheses disagree, {@link #unanimous()} is false and
 * {@link #disagreeingHypothesisIds()} names the ones that disagree. The {@code value} field then
 * carries the value the MAJORITY of the surviving hypotheses hold, and in an exact tie the value
 * of the hypothesis with the smallest identifier — a deterministic rule, chosen for
 * reproducibility and for nothing else. A consumer that must not guess has to test
 * {@link #unanimous()} first; that is the whole reason the field exists next to the flag.</p>
 *
 * <p><b>Why the supporting cases are listed.</b> A prediction without its evidence reads like a
 * fact. Listing the observations whose outcome agrees with the predicted value lets a reader see
 * what the prediction actually rests on.</p>
 */
public record HypothesisPrediction(String subject, boolean value, boolean unanimous,
                                   List<String> disagreeingHypothesisIds,
                                   List<String> supportingCaseIds) {

    public HypothesisPrediction {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("a prediction must say what it is about");
        }
        disagreeingHypothesisIds = disagreeingHypothesisIds == null
                ? List.of() : List.copyOf(disagreeingHypothesisIds);
        supportingCaseIds = supportingCaseIds == null ? List.of() : List.copyOf(supportingCaseIds);
    }

    /** Whether the session was sure. A prediction that was not sure is not a claim. */
    public boolean decided() {
        return unanimous;
    }

    @Override
    public String toString() {
        return "HypothesisPrediction[" + subject + " value=" + value + " unanimous=" + unanimous
                + " disagreeing=" + disagreeingHypothesisIds.size() + "]";
    }
}
