package io.matrix.cognitive.algorithms;

/**
 * W44.2 — a computation refused to answer.
 *
 * <p>A refusal is not a failure and not a resource limit. The problem was well-formed, the budget
 * was sufficient, and the answer does not exist: a shortest-path graph with a negative-weight
 * cycle reachable from the source has no shortest path, and the honest answer is that there is
 * none rather than the smallest number the relaxation happened to produce.</p>
 *
 * <p>It is a separate type from {@link ResourceLimitException} because the two map to different
 * decision statuses: a refusal is {@link AlgorithmDecision.Status#REFUSED}, an exhausted budget
 * is {@link AlgorithmDecision.Status#RESOURCE_LIMITED}. Collapsing them would hide the
 * difference between "this cannot be done" and "this could not be finished".</p>
 */
public final class AlgorithmRefusalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message why the computation refused
     */
    public AlgorithmRefusalException(String message) {
        super(message);
    }

    /**
     * @param message why the computation refused
     * @param cause   the underlying cause
     */
    public AlgorithmRefusalException(String message, Throwable cause) {
        super(message, cause);
    }
}
