package io.matrix.contract;

/**
 * W51 — the reply to a command.
 *
 * <p><b>What this replaces.</b> Nothing. The two chains had no shared reply format, so a
 * refusal could not be distinguished from a failure, and a failure could not be distinguished
 * from a timeout. Those are three different facts and the W51 failure list names all three:
 * "a plugin failing during initialization", "a timeout", and "an attempted operation outside
 * granted capabilities".</p>
 *
 * <p><b>The status is the point.</b> A response is not a payload with an error field; it is one
 * of five dispositions, and each of them says something different to the caller:</p>
 * <ul>
 *   <li>{@link Status#ACCEPTED} — the work was done and the payload carries the result;</li>
 *   <li>{@link Status#REJECTED} — the receiver understood the message and declined it: an
 *       incompatible contract version, a missing capability, a refused precondition. A retry
 *       does not help, and the reason says which;</li>
 *   <li>{@link Status#FAILED} — the receiver tried and could not. The reason carries the
 *       failure;</li>
 *   <li>{@link Status#TIMED_OUT} — the caller gave up. The work may or may not have been done,
 *       which is exactly why the command carried an idempotency key;</li>
 *   <li>{@link Status#DUPLICATE} — the command was already applied under the same idempotency
 *       key, and the payload carries the ORIGINAL result. This is the status that makes a
 *       retried command safe.</li>
 * </ul>
 *
 * @param status     the disposition of the command
 * @param reason     why the command ended as it did; never blank for a non-accepted status
 * @param payload    the JSON result, or the JSON detail of a refusal
 * @param correlation the causality metadata of the reply
 * @param commandId  the identity of the command this replies to
 * @param contractVersion the contract version the reply speaks
 */
public record CognitiveResponse(
        Status status,
        String reason,
        String payload,
        Correlation correlation,
        String commandId,
        int contractVersion) {

    /** The disposition of a command. */
    public enum Status {
        /** The work was done. */
        ACCEPTED,
        /** The receiver understood the message and declined it. */
        REJECTED,
        /** The receiver tried and could not. */
        FAILED,
        /** The caller gave up waiting. */
        TIMED_OUT,
        /** The command was already applied under the same idempotency key. */
        DUPLICATE
    }

    public CognitiveResponse {
        if (status == null) {
            throw new IllegalArgumentException("response status must not be null");
        }
        if (payload == null) {
            payload = "{}";
        }
        if (status != Status.ACCEPTED && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("a non-accepted response must say why");
        }
    }

    /** A successful reply. */
    public static CognitiveResponse accepted(String payload, Correlation correlation,
                                              String commandId) {
        return new CognitiveResponse(Status.ACCEPTED, "the command was applied",
                payload, correlation, commandId, CognitiveContract.VERSION);
    }

    /** A refusal. */
    public static CognitiveResponse rejected(String reason, Correlation correlation,
                                              String commandId) {
        return new CognitiveResponse(Status.REJECTED, reason, "{}", correlation, commandId,
                CognitiveContract.VERSION);
    }

    /** A failure. */
    public static CognitiveResponse failed(String reason, Correlation correlation, String commandId) {
        return new CognitiveResponse(Status.FAILED, reason, "{}", correlation, commandId,
                CognitiveContract.VERSION);
    }

    /** A reply to a command that had already been applied. */
    public static CognitiveResponse duplicate(String originalPayload, Correlation correlation,
                                              String commandId) {
        return new CognitiveResponse(Status.DUPLICATE,
                "the command was already applied under the same idempotency key",
                originalPayload, correlation, commandId, CognitiveContract.VERSION);
    }

    /** True when the command succeeded. */
    public boolean successful() {
        return status == Status.ACCEPTED || status == Status.DUPLICATE;
    }

    @Override
    public String toString() {
        return "CognitiveResponse[" + status + " " + commandId + ": " + reason + "]";
    }
}
