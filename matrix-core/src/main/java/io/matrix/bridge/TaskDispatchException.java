package io.matrix.bridge;

/**
 * W53 — the dispatch of a remote cognitive task did not reach an answer.
 *
 * <p>The failure is reported as an exception rather than as a result, because there is no
 * answer to evaluate: the peer was never reached, or the exchange broke before it could
 * answer. A caller that catches this has no report, no result and no epistemic claim to
 * make — and that is exactly what it must record: a task that could not be dispatched is
 * not a task that failed to produce the right answer.</p>
 */
public final class TaskDispatchException extends RuntimeException {

    /**
     * The failure.
     *
     * @param message what went wrong
     * @param cause   the cause, when there is one
     */
    public TaskDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
