package io.matrix.contract;

import java.util.List;

/**
 * W51 — what the system expected to happen, recorded before it happened.
 *
 * <p><b>The rule this record exists to enforce.</b> A prediction is made BEFORE the action and
 * is not reconstructed afterwards. The W51 requirement says so directly: "Do not reconstruct a
 * prediction retrospectively from the outcome." A record that is written after the observation
 * carries no information: it says what happened, dressed as what was expected.</p>
 *
 * <p><b>The record is settled before it can be compared.</b> A prediction that can still be
 * edited is not a prediction. {@link #settledAt} says when it was frozen, and the durable
 * store of the kernel writes the prediction before the probe — that ordering is what makes the
 * comparison meaningful.</p>
 *
 * <p><b>A prediction names what it was based on.</b> {@link #basedOnHypothesisIds} says which
 * hypotheses it discriminated between, and {@link #rationale} says why the system expected
 * that. Without them, a correct prediction teaches nothing: the system cannot tell whether it
 * was right for a reason or by luck.</p>
 *
 * @param id                 the identity of the prediction
 * @param subjectId          the thing the prediction is about
 * @param expectedOutcome    what the system expected
 * @param confidence         how sure the system was
 * @param rationale          why the system expected it
 * @param basedOnHypothesisIds the hypotheses the prediction discriminated between
 * @param madeAt             when the prediction was made
 * @param settledAt          when the prediction was frozen, or {@code null} while it is open
 * @param expectedInformationGain the information the prediction was expected to yield
 */
public record Prediction(
        String id,
        String subjectId,
        String expectedOutcome,
        Confidence confidence,
        String rationale,
        List<String> basedOnHypothesisIds,
        String madeAt,
        String settledAt,
        double expectedInformationGain) {

    public Prediction {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("prediction id must not be blank");
        }
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("prediction subject id must not be blank");
        }
        if (expectedOutcome == null || expectedOutcome.isBlank()) {
            throw new IllegalArgumentException("prediction expected outcome must not be blank");
        }
        if (confidence == null) {
            confidence = Confidence.UNESTABLISHED;
        }
        basedOnHypothesisIds = basedOnHypothesisIds == null ? List.of() : List.copyOf(basedOnHypothesisIds);
        if (Double.isNaN(expectedInformationGain) || expectedInformationGain < 0.0) {
            throw new IllegalArgumentException("expected information gain must not be negative");
        }
    }

    /** True when the prediction has been frozen and can be compared with an outcome. */
    public boolean settled() {
        return settledAt != null && !settledAt.isBlank();
    }

    /** True when the prediction discriminates between at least two hypotheses. */
    public boolean discriminating() {
        return basedOnHypothesisIds.size() > 1;
    }

    /**
     * The frozen form of this prediction.
     *
     * @param at the instant the prediction was frozen
     * @return the settled prediction
     */
    public Prediction settledAt(String at) {
        return new Prediction(id, subjectId, expectedOutcome, confidence, rationale,
                basedOnHypothesisIds, madeAt, at, expectedInformationGain);
    }

    @Override
    public String toString() {
        return "Prediction[" + id + " " + expectedOutcome + " settled=" + settled() + "]";
    }
}
