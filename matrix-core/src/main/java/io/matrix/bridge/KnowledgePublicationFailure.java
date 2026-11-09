package io.matrix.bridge;

/**
 * W52 — the publication of established knowledge failed in a way the sender must know about.
 *
 * <p><b>Only the transport fails this way.</b> A refusal by the receiver — a forged
 * signature, a right that was never granted, a budget that is exhausted — is an ANSWER, and
 * an answer is returned as it is. This failure is for the case where there was no answer:
 * the receiver did not answer at all within the attempts the retry policy allows.</p>
 *
 * <p><b>The cause is kept.</b> A publication that failed says why, and the reason is the
 * difference between "chain B is down" and "chain B is refusing my messages" — two
 * problems that look the same to an operator and need different answers.</p>
 */
public final class KnowledgePublicationFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message what went wrong
     */
    public KnowledgePublicationFailure(String message) {
        super(message);
    }

    /**
     * @param message what went wrong
     * @param cause   the underlying failure
     */
    public KnowledgePublicationFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
