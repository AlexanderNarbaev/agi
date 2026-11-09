package io.matrix.brain.runtime.bridge;

/**
 * W51 — the failure of a publication over the bridge.
 *
 * <p><b>Why this is its own type.</b> A publisher that cannot reach chain A must not be
 * mistaken for a publisher whose message was refused: the first means "the outcome is
 * unknown", the second means "the receiver said no". The first is the dangerous one, because
 * the message may have been applied — which is exactly why the request id is the idempotency
 * key and why a retry of the same request id is answered with the original outcome.</p>
 *
 * <p>The failure is unchecked because a brain runtime's publication is a background activity:
 * a caller that wants to handle it can, and a caller that does not should not be forced to
 * wrap every call in a try/catch. What it must not do is treat the failure as a refusal.</p>
 */
public class BridgePublicationFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message what went wrong
     * @param cause   the cause
     */
    public BridgePublicationFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
