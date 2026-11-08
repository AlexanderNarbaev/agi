package io.matrix.contract;

import java.util.List;

/**
 * W51 — what actually happened when an action was performed.
 *
 * <p><b>An outcome is a measurement, not an interpretation.</b> It says what was observed and
 * whether the expectation was met. The INTERPRETATION — the error, the belief revision, the
 * lesson — is a separate act with a separate record, and the W51 requirement that "the
 * observed result is compared with the prediction" is a comparison between two records rather
 * than a sentence written after the fact.</p>
 *
 * <p><b>The comparison is explicit.</b> {@link #predictionId()} and {@link #matched()} say
 * which prediction this outcome answers and whether it matched. An outcome with no prediction
 * is not a failed prediction; it is an observation with no expectation, and the difference
 * matters: only the first can teach the system that a prediction was wrong.</p>
 *
 * <p><b>A failure is an outcome.</b> {@link Status#FAILED} with a reason is a measurement
 * that the action did not succeed, and it is kept with the same care as a success.</p>
 *
 * @param id             the identity of the outcome
 * @param actionId       the action the outcome answers
 * @param observationId  the observation that produced the outcome
 * @param predictionId   the prediction the outcome answers, or {@code null}
 * @param status         the status of the action
 * @param observed       what was observed
 * @param matched        whether the observation matched the prediction; {@code null} when there
 *                       was no prediction
 * @param error          the magnitude of the discrepancy, or {@code null} when there was no
 *                       prediction
 * @param reason         why the action failed, when it failed
 * @param at             when the outcome was recorded
 */
public record Outcome(
        String id,
        String actionId,
        String observationId,
        String predictionId,
        Status status,
        String observed,
        Boolean matched,
        Double error,
        String reason,
        String at) {

    /** The status of a performed action. */
    public enum Status {
        /** The action was performed and did what it was supposed to do. */
        SUCCEEDED,
        /** The action was performed and its effect differed from the expectation. */
        DEVIATED,
        /** The action could not be performed. */
        FAILED,
        /** The action was not permitted. */
        REFUSED
    }

    public Outcome {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("outcome id must not be blank");
        }
        if (actionId == null || actionId.isBlank()) {
            throw new IllegalArgumentException("outcome action id must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("outcome status must not be null");
        }
        if (error != null && (Double.isNaN(error) || error < 0.0)) {
            throw new IllegalArgumentException("outcome error must not be negative");
        }
    }

    /**
     * An outcome that answers a prediction.
     *
     * @param id            the identity of the outcome
     * @param actionId      the action the outcome answers
     * @param observationId the observation the outcome is built on
     * @param predictionId  the prediction the outcome answers
     * @param matched       whether the observation matched the prediction
     * @param error         the magnitude of the discrepancy
     * @param observed      what was observed
     * @param at            when the outcome was recorded
     * @return the outcome
     */
    public static Outcome against(String id, String actionId, String observationId,
                                   String predictionId, boolean matched, double error,
                                   String observed, String at) {
        return new Outcome(id, actionId, observationId, predictionId,
                matched ? Status.SUCCEEDED : Status.DEVIATED, observed, matched, error, null, at);
    }

    /** True when there was a prediction to compare with. */
    public boolean comparable() {
        return predictionId != null && matched != null;
    }

    /** True when the outcome shows the system was right. */
    public boolean correct() {
        return Boolean.TRUE.equals(matched);
    }

    /** True when the outcome shows the system was wrong. */
    public boolean incorrect() {
        return Boolean.FALSE.equals(matched);
    }

    @Override
    public String toString() {
        return "Outcome[" + id + " " + status + " matched=" + matched + "]";
    }
}
