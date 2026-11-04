package io.matrix.knowledge;

import io.matrix.cognitive.microworld.ObservationDisposition;

/**
 * W45.1 — ONE decision about a canonical case, and the row the history keeps.
 *
 * <p><b>Why the history is append-only and separate from the projection.</b> The disposition
 * of a case is the CURRENT answer to "may this observation constrain the model". It changes:
 * a contradiction is held, a review accepts it, a later review rejects it. The history is the
 * account of HOW it got there, and it is never rewritten — a decision that is overturned is
 * still a decision that was taken, and a system whose value is its history cannot lose it.
 * The current disposition is therefore a PROJECTION of this history: it can be recomputed
 * from it, and the store can say whether the two agree.</p>
 *
 * <p><b>Why a decision has a stable id.</b> A review is a request that may be delivered
 * twice, retried, or replayed after a restart. Without a stable id there is no way to tell a
 * second delivery of the SAME decision from a second, different decision about the same case,
 * and the store would either write the decision twice or overwrite the first one. With the
 * id, the two are told apart: the same id with the same content is an idempotent no-op, and
 * the same id with DIFFERENT content is a conflict — never a silent overwrite.</p>
 *
 * <p><b>Why the previous disposition is in the row.</b> A decision is not only what was
 * decided but what it decided INSTEAD OF. Without the previous disposition the history is a
 * list of states and not a list of changes, and the reader cannot tell a first decision from
 * a re-opening. The store computes it: it is the state the case was in when the decision was
 * taken, and the caller cannot assert it.</p>
 *
 * @param decisionId               the stable identifier of the decision
 * @param workspaceId              the workspace
 * @param caseId                   the canonical case the decision is about
 * @param previousDisposition      what the case was before the decision; null for the first one
 * @param disposition              what the case became
 * @param reason                   why
 * @param contradictedPredictionId the prediction the observation contradicted, if any
 * @param resolvedBy               who or what decided
 * @param resolvedAt               when it was decided
 * @param modelRevisionId          the model revision that took the observation in, if any
 * @param recordedAt               when the decision was recorded
 * @param requestId                the request or episode the decision came from, if known
 */
public record ObservationAdjudication(
        String decisionId,
        String workspaceId,
        String caseId,
        ObservationDisposition previousDisposition,
        ObservationDisposition disposition,
        String reason,
        String contradictedPredictionId,
        String resolvedBy,
        String resolvedAt,
        String modelRevisionId,
        String recordedAt,
        String requestId) {

    public ObservationAdjudication {
        if (decisionId == null || decisionId.isBlank()) {
            throw new IllegalArgumentException("a decision id must not be blank");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("a decision workspace id must not be blank");
        }
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("a decision must name the case it decides about");
        }
        if (disposition == null) {
            throw new IllegalArgumentException("a decision must say what it decides");
        }
        if (recordedAt == null || recordedAt.isBlank()) {
            throw new IllegalArgumentException("a decision must say when it was recorded");
        }
    }

    /** The prefix of the identifiers this store derives for decisions. */
    public static final String ID_PREFIX = "adj-";

    /**
     * A decision that has not been taken yet.
     *
     * <p>The previous disposition is left empty on purpose: it is the store's to compute, and
     * a caller that could assert it could claim a transition that did not happen.</p>
     *
     * @param decisionId               the stable identifier of the decision
     * @param workspaceId              the workspace
     * @param caseId                   the canonical case
     * @param disposition              what the case becomes
     * @param reason                   why
     * @param contradictedPredictionId the prediction the observation contradicted, if any
     * @param resolvedBy               who or what decided
     * @param resolvedAt               when it was decided
     * @param modelRevisionId          the model revision that took the observation in, if any
     * @param recordedAt               when the decision is recorded
     * @param requestId                the request or episode the decision came from, if known
     * @return the decision
     */
    public static ObservationAdjudication decide(String decisionId, String workspaceId, String caseId,
                                                 ObservationDisposition disposition, String reason,
                                                 String contradictedPredictionId, String resolvedBy,
                                                 String resolvedAt, String modelRevisionId,
                                                 String recordedAt, String requestId) {
        return new ObservationAdjudication(decisionId, workspaceId, caseId, null, disposition,
                reason, contradictedPredictionId, resolvedBy, resolvedAt, modelRevisionId,
                recordedAt, requestId);
    }

    /**
     * Whether this decision says the same thing as another one.
     *
     * <p>The previous disposition is deliberately NOT part of the comparison: it is computed by
     * the store from the state the case was in, so two deliveries of the same decision would
     * compute it the same way, and a difference in it is a difference in the STATE, which the
     * store reports as a conflict of its own.</p>
     *
     * @param other the decision to compare with
     * @return true when the two decisions carry the same content
     */
    public boolean sameContentAs(ObservationAdjudication other) {
        return other != null
                && decisionId.equals(other.decisionId())
                && workspaceId.equals(other.workspaceId())
                && caseId.equals(other.caseId())
                && disposition == other.disposition()
                && equal(reason, other.reason())
                && equal(contradictedPredictionId, other.contradictedPredictionId())
                && equal(resolvedBy, other.resolvedBy())
                && equal(resolvedAt, other.resolvedAt())
                && equal(modelRevisionId, other.modelRevisionId())
                && equal(recordedAt, other.recordedAt())
                && equal(requestId, other.requestId());
    }

    /** Whether two strings say the same thing, treating a null as an empty string. */
    private static boolean equal(String left, String right) {
        return (left == null ? "" : left).equals(right == null ? "" : right);
    }
}
