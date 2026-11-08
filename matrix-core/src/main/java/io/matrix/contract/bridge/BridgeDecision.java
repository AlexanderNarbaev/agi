package io.matrix.contract.bridge;

/**
 * W51 — the answer the bridge gives to a message (BRIDGE-6).
 *
 * <p><b>Two outcomes, and one of them is the default.</b> BRIDGE-6 says "the default is 'no'"
 * and that "a bridge that cannot decide does not write". A decision therefore has two states
 * and no third: the message was accepted, or it was refused. A "maybe", a "queued" or a
 * "deferred" state would be a state in which the caller cannot tell whether the effect
 * happened, and BRIDGE-8's promise — "a caller that retries after a timeout gets the outcome
 * of the first attempt" — is impossible to keep with such a state.</p>
 *
 * <p><b>Why a refusal must carry a reason.</b> BRIDGE-7 writes the refusal into the same
 * append-only chain as the acceptance, "as carefully as the acceptance", and the record
 * carries "the reason for the decision". A refusal without a reason cannot be audited, cannot
 * be counted, and cannot be repaired: a reader of the chain could not tell an exhausted quota
 * from a forged signature. The constructor refuses to build a refusal without one.</p>
 *
 * <p><b>Why the audit id is in the decision.</b> The caller needs to be able to point at the
 * record that was written for its call. The identifier is therefore decided before the
 * decision is built and travels with it, so the answer and the record always name each
 * other.</p>
 *
 * @param status    whether the message was accepted or refused (BRIDGE-6)
 * @param requestId the request the decision is about (BRIDGE-8)
 * @param auditId   the identifier of the audit record that was written for this call (BRIDGE-7)
 * @param reason    why the decision was made; never blank
 */
public record BridgeDecision(
        Status status,
        String requestId,
        String auditId,
        String reason) {

    /** Whether a message was accepted or refused. */
    public enum Status {
        /** The message satisfied the contract and its effect was recorded. */
        ACCEPTED,
        /** The message did not satisfy the contract; nothing was written. */
        REFUSED
    }

    /** The reason recorded for an accepted message. */
    public static final String ACCEPTED_REASON = "the message satisfies the contract";

    public BridgeDecision {
        if (status == null) {
            throw new IllegalArgumentException("a bridge decision needs a status");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("a bridge decision needs a request id");
        }
        if (auditId == null || auditId.isBlank()) {
            throw new IllegalArgumentException("a bridge decision needs an audit id");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "a bridge decision needs a reason; a decision without one cannot be audited");
        }
    }

    /**
     * The answer to a message that satisfied the contract.
     *
     * @param requestId the request the answer is about
     * @param auditId   the identifier of the audit record
     * @return the decision
     */
    public static BridgeDecision accepted(String requestId, String auditId) {
        return new BridgeDecision(Status.ACCEPTED, requestId, auditId, ACCEPTED_REASON);
    }

    /**
     * The answer to a message that did not satisfy the contract (BRIDGE-6).
     *
     * @param requestId the request the answer is about
     * @param auditId   the identifier of the audit record
     * @param reason    why the message was refused
     * @param detail    what was wrong, in words
     * @return the decision
     */
    public static BridgeDecision refused(String requestId, String auditId,
                                         BridgeRefusalReason reason, String detail) {
        BridgeRefusalReason cause = reason == null
                ? BridgeRefusalReason.MALFORMED_MESSAGE : reason;
        String text = detail == null || detail.isBlank()
                ? cause.description() : cause.description() + ": " + detail;
        return new BridgeDecision(Status.REFUSED, requestId, auditId, text);
    }

    /** True when the message was accepted. */
    public boolean accepted() {
        return status == Status.ACCEPTED;
    }

    /** True when the message was refused. */
    public boolean refused() {
        return status == Status.REFUSED;
    }
}
