package io.matrix.cognitive;

/**
 * W45.1 — the failure of a request whose outcome cannot be established.
 *
 * <h2>Why this is not a conflict</h2>
 * <p>A {@link CognitiveConflictException} means the request contradicts a record: the caller
 * sent a key that was used for a different payload, or asked for a fact that is about to be
 * rewritten. The record is intact and the caller can correct its request.</p>
 *
 * <p>This exception means something different: the request was interrupted after it had
 * already committed durable work, so its outcome is UNKNOWN. Nothing contradicts the record,
 * and correcting the request would not help: the work may already have happened. The only
 * truthful answer is to say so, and the only safe one is not to repeat the work.</p>
 *
 * <h2>What the caller may do</h2>
 * <ul>
 *   <li>read the episode of the request and see what was committed;</li>
 *   <li>use a NEW idempotency key if it wants a new attempt;</li>
 *   <li>NOT retry the same key blindly — the effect may happen twice.</li>
 * </ul>
 */
public class CognitiveRequestFailure extends RuntimeException {

    /** The kind of failure. */
    public enum Kind {
        /**
         * The request was interrupted after it had committed durable work, and its outcome
         * cannot be established.
         */
        INDETERMINATE,
        /**
         * The request was interrupted and its recovery was refused; it needs a human
         * decision.
         */
        RECOVERY_REFUSED
    }

    /** The kind of the failure. */
    private final Kind kind;

    /**
     * A failure of a request.
     *
     * @param kind    the kind of the failure
     * @param message what the caller is told
     */
    public CognitiveRequestFailure(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    /**
     * The kind of the failure.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }
}
