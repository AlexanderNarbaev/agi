package io.matrix.contract.bridge;

/**
 * W51 — a message this reader cannot read (BRIDGE-6).
 *
 * <p><b>Why an exception and not a null.</b> A decoder that returns {@code null} for a
 * message it cannot read has thrown away the reason, and BRIDGE-6 requires the refusal to
 * carry one: "an unknown version, a failed authentication, a missing provenance, an exhausted
 * quota, a timeout, a malformed message" — each produces a refusal and an audit record, and
 * the audit record has to say which of them happened. The exception carries the reason so that
 * the caller can turn it into a refusal instead of inventing one.</p>
 *
 * <p><b>Why it is unchecked.</b> The bridge protocol is used from a message handler, where a
 * checked exception would either force a {@code catch} in every call site or force a
 * declaration that says nothing. The failure it names is an ordinary outcome of reading
 * untrusted input, not a programming error, and it is handled at exactly one place — the
 * receiver — which turns it into a {@link BridgeDecision}.</p>
 */
public class BridgeProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final BridgeRefusalReason reason;

    /**
     * A message this reader cannot read.
     *
     * @param reason  why it was refused
     * @param message what was wrong, in words
     */
    public BridgeProtocolException(BridgeRefusalReason reason, String message) {
        super(message);
        this.reason = reason == null ? BridgeRefusalReason.MALFORMED_MESSAGE : reason;
    }

    /**
     * A message this reader cannot read, with the underlying failure.
     *
     * @param reason  why it was refused
     * @param message what was wrong, in words
     * @param cause   the failure underneath
     */
    public BridgeProtocolException(BridgeRefusalReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason == null ? BridgeRefusalReason.MALFORMED_MESSAGE : reason;
    }

    /** Why the message was refused (BRIDGE-6). */
    public BridgeRefusalReason reason() {
        return reason;
    }
}
