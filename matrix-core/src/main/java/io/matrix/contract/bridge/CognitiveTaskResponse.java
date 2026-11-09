package io.matrix.contract.bridge;

import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;

import java.util.List;

/**
 * W53 — the structured answer to a remote cognitive task.
 *
 * <p><b>The rule this record exists to enforce.</b> <i>Accepting a request and completing
 * its cognitive execution are different states.</i> A response of {@link Status#COMPLETED}
 * means that a capability ran and produced a result; it does <b>not</b> mean that the
 * result is true. The status vocabulary keeps the states apart, and the confidence carries
 * an explicit <b>basis</b> so that a reader can tell a measured number from a declared
 * one.</p>
 *
 * <p><b>What is optional, and why.</b> The hypotheses, the prediction, the outcome and the
 * supporting observations are nullable. The mandate says: <i>do not fabricate reasoning
 * steps that the production implementation does not expose</i>. The capability that
 * executes the first remote task exposes a result, a confidence, the modulators it fired
 * and its trace — and nothing else. The fields that the production path does not expose
 * stay empty rather than being filled with a plausible story, and a reader can see that
 * they are empty.</p>
 *
 * <p><b>The binding to the request.</b> The response echoes the request's payload hash, so
 * the caller can bind the answer to the request it asked, and the responder's identity and
 * the capability it used, so that the caller can check the origin before it reads the
 * content.</p>
 *
 * @param taskId          the task the response answers
 * @param requestId       the attempt the response answers
 * @param correlation     the correlation of the task
 * @param requestPayloadHash the hash of the request this response answers
 * @param responderId     the identity of the process that executed the task
 * @param capabilityId    the capability that was used
 * @param capabilityVersion the version of that capability
 * @param status          what happened
 * @param result          the result of the execution, when there is one
 * @param confidence      the confidence the responder reports, when it reports one
 * @param confidenceBasis what the confidence rests on
 * @param hypotheses      the hypotheses the capability considered, when it exposes them
 * @param prediction      the prediction the capability made, when it exposes one
 * @param outcome         the outcome the capability observed, when it exposes one
 * @param supportingObservations the observations that support the result, when exposed
 * @param contradictions  the contradictions the capability found, when it exposes them
 * @param limitations     what the result does not cover
 * @param trace           the steps of the execution the capability exposed
 * @param provenance      where the result came from
 * @param methodRationale why the capability was used, when it says
 * @param durationMillis  how long the execution took
 * @param traceReference  a reference to the durable execution trace
 * @param refusalReason   the machine-readable reason, when the status is not COMPLETED
 * @param at              the instant the response was written
 */
public record CognitiveTaskResponse(
        String taskId,
        String requestId,
        Correlation correlation,
        String requestPayloadHash,
        String responderId,
        String capabilityId,
        String capabilityVersion,
        Status status,
        String result,
        Double confidence,
        ConfidenceBasis confidenceBasis,
        List<String> hypotheses,
        String prediction,
        String outcome,
        List<String> supportingObservations,
        List<String> contradictions,
        List<String> limitations,
        List<String> trace,
        Provenance provenance,
        String methodRationale,
        Long durationMillis,
        String traceReference,
        String refusalReason,
        String at) {

    /** The states of a remote cognitive execution, kept apart on purpose. */
    public enum Status {
        /** The capability executed and produced a result. */
        COMPLETED,
        /** The capability produced a result that does not cover the whole request. */
        PARTIAL,
        /** The request was refused by policy: a right, a budget or a constraint. */
        REFUSED,
        /** The capability does not exist or does not serve this kind of task. */
        UNSUPPORTED,
        /** The execution failed — a technical failure, not an incorrect inference. */
        FAILED,
        /** The deadline passed before the result was ready. */
        TIMED_OUT,
        /** The request was cancelled; the reason says when. */
        CANCELLED,
        /** The execution model requires it: the task is running and will answer later. */
        IN_PROGRESS;

        /**
         * The status named on the wire.
         *
         * @param wireName the name
         * @return the status, or empty when the protocol does not define it
         */
        public static java.util.Optional<Status> parse(String wireName) {
            if (wireName == null || wireName.isBlank()) {
                return java.util.Optional.empty();
            }
            for (Status status : values()) {
                if (status.name().equals(wireName)) {
                    return java.util.Optional.of(status);
                }
            }
            return java.util.Optional.empty();
        }
    }

    /** What a reported confidence rests on. */
    public enum ConfidenceBasis {
        /** The number is a declared property of the capability, not a measurement. */
        DECLARED,
        /** The number was measured during this execution. */
        MEASURED,
        /** The number rests on independent evidence the responder holds. */
        INDEPENDENT,
        /** The responder reports no confidence. */
        UNKNOWN;

        /**
         * The basis named on the wire.
         *
         * @param wireName the name
         * @return the basis, or empty when the protocol does not define it
         */
        public static java.util.Optional<ConfidenceBasis> parse(String wireName) {
            if (wireName == null || wireName.isBlank()) {
                return java.util.Optional.empty();
            }
            for (ConfidenceBasis basis : values()) {
                if (basis.name().equals(wireName)) {
                    return java.util.Optional.of(basis);
                }
            }
            return java.util.Optional.empty();
        }
    }

    public CognitiveTaskResponse {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("the task response needs a task id");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("the task response needs a request id");
        }
        if (correlation == null) {
            throw new IllegalArgumentException("the task response needs a correlation");
        }
        if (responderId == null || responderId.isBlank()) {
            throw new IllegalArgumentException("the task response needs a responder identity");
        }
        if (capabilityId == null || capabilityId.isBlank()) {
            throw new IllegalArgumentException("the task response needs the capability it used");
        }
        if (status == null) {
            throw new IllegalArgumentException("the task response needs a status");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("the task response needs its provenance");
        }
        if (status != Status.COMPLETED && status != Status.PARTIAL
                && (refusalReason == null || refusalReason.isBlank())) {
            throw new IllegalArgumentException("a response that is not a result must say why");
        }
        hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
        supportingObservations = supportingObservations == null
                ? List.of() : List.copyOf(supportingObservations);
        contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        trace = trace == null ? List.of() : List.copyOf(trace);
        if (confidence != null && confidenceBasis == null) {
            throw new IllegalArgumentException("a confidence must say what it rests on");
        }
    }

    /**
     * Whether the execution produced a result the caller may evaluate.
     *
     * @return true when the status is COMPLETED or PARTIAL
     */
    public boolean carriesResult() {
        return status == Status.COMPLETED || status == Status.PARTIAL;
    }

    /**
     * Whether the response binds itself to the request it answers.
     *
     * @param request the request
     * @return true when the payload hash matches
     */
    public boolean answers(CognitiveTaskRequest request) {
        return request != null
                && request.requestId().equals(requestId)
                && request.taskId().equals(taskId)
                && request.payloadHash().equals(requestPayloadHash);
    }
}
