package io.matrix.cognitive.algorithms;

/**
 * W44.2 — a computation stopped because it ran out of budget.
 *
 * <p>The W44 directive is explicit that an exhausted budget must never be reported as a
 * successfully proven result. A method that hits its budget therefore cannot return a value
 * dressed up as an answer: it throws this, and the partial state travels with the exception so
 * that a caller can show what was found without claiming it was proven.</p>
 *
 * <p>The partial result is carried as an {@link Object} because the portfolio spans families and
 * each family has its own output type. A caller casts it to its own family type, exactly as it
 * would cast the payload of a {@link ProblemRequest}.</p>
 */
public final class ResourceLimitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Object partialResult;

    /**
     * @param message       what was exhausted
     * @param partialResult what was known when the budget ran out — never a proven answer
     */
    public ResourceLimitException(String message, Object partialResult) {
        super(message);
        this.partialResult = partialResult;
    }

    /** The bounded, explicitly unproven state at the moment the budget ran out. */
    public Object partialResult() {
        return partialResult;
    }
}
