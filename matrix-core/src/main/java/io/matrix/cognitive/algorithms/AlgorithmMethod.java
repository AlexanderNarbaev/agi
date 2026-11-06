package io.matrix.cognitive.algorithms;

/**
 * W44.2 — one method the selector is allowed to choose.
 *
 * <p>The portfolio is a set of typed methods. A method declares what it can do
 * ({@link AlgorithmCapability}) and refuses to run outside its declared preconditions.</p>
 *
 * <p>The generic parameters are the typing the W44 directive asks for: each method has its own
 * input record and its own output record, and the portfolio only ever passes the payload it was
 * given. The untyped step is the SELECTOR's, and it is explicit: {@link #preconditionsHold} is
 * asked of the payload itself, so a method that lies about its preconditions is caught by the
 * method and not only by the selector.</p>
 *
 * <p>A method that cannot honour its budget must report that it could not
 * ({@link AlgorithmDecision.Status#RESOURCE_LIMITED}) rather than return a truncated result as if
 * it were complete.</p>
 */
public interface AlgorithmMethod<I, O> {

    /** What this method declares about itself. */
    AlgorithmCapability capability();

    /**
     * Whether the declared preconditions hold for this payload.
     *
     * <p>Called by the selector before the method is chosen, and again by the method itself
     * before it computes anything.</p>
     */
    boolean preconditionsHold(I input);

    /**
     * Run the method.
     *
     * @throws IllegalStateException when a precondition does not hold
     */
    O execute(I input, ComputeBudget budget);
}
