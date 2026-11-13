package io.matrix.cognitive.algorithms;

/**
 * W53 — what kind of outcome a measured call of a method had.
 *
 * <p><b>Why the boolean was not enough.</b> The ledger of measured methods recorded one bit:
 * did the call complete without throwing. That is a <i>technical</i> fact, and the mandate
 * asks a different question: "Do not let a high completion rate masquerade as high reasoning
 * accuracy." A method that answers every question and is wrong half the time looks exactly as
 * good as a method that answers every question and is right — and a method that abstains looks
 * worse than either, although an abstention is not a failure.</p>
 *
 * <p><b>What the kinds are.</b> The five kinds that <i>decide</i> — the method said something
 * about the world — are {@link #VALIDATED}, {@link #INCORRECT}, {@link #PARTIAL},
 * {@link #QUALIFIED} and {@link #UNSUPPORTED}. They are the trials a reliability estimate is
 * computed over. The kinds
 * that are <i>not evidence</i> about the reasoning — the call threw, timed out, was refused,
 * was cancelled, or merely returned without anything confirming it — are counted separately
 * and never enter the estimate's denominator.</p>
 *
 * <p><b>What an abstention is.</b> An abstention is the method saying "I do not know". It is
 * not a wrong answer, and it is not a technical failure either: it is the method declining to
 * assert. That is why it is counted apart from the decisions.</p>
 *
 * <p><b>What UNVERIFIED is.</b> The call returned something and nothing confirmed it. It is
 * the completion rate's bucket, and the completion rate is reported as a completion rate — it
 * is never an accuracy. The system has no verifier at the moment a local method returns, and
 * pretending otherwise would be the very confusion the taxonomy exists to prevent.</p>
 */
public enum MethodOutcome {

    /** The result was correct and validated against evidence. */
    VALIDATED,

    /** The method answered and the answer was wrong. */
    INCORRECT,

    /** The method answered and the answer covered only part of the question. */
    PARTIAL,

    /**
     * The method answered, and the answer was supported — but not independently.
     *
     * <p><b>Why this kind is its own.</b> A report that chain A's own evidence supports, but
     * only because it rests on what chain A supplied, is neither a validated result nor an
     * unsupported one: the support is real and it is not independent. Folding it into
     * {@link #VALIDATED} would count the peer's own echo of chain A's material as the peer's
     * success, and folding it into {@link #UNSUPPORTED} would deny a support that exists.
     * It is a decided trial — the verifier decided something about it — and it is not a
     * validated one, so it belongs in the denominator and not in the numerator.</p>
     */
    QUALIFIED,

    /** The method answered and the answer rested on nothing that supports it. */
    UNSUPPORTED,

    /** The method declined to assert: an explicit abstention. */
    ABSTAINED,

    /** The call returned and nothing confirmed the result. */
    UNVERIFIED,

    /** The call threw. */
    TECHNICAL_FAILURE,

    /** The call ran out of its budget. */
    TIMEOUT,

    /** The call was refused — a capability was missing, or a right was not granted. */
    REFUSED,

    /** The call was cancelled by the caller. */
    CANCELLED;

    /**
     * True when this outcome says something about the method's reasoning.
     *
     * <p>These are the outcomes a reliability estimate is computed over: the method asserted
     * something, and the assertion was either right, wrong, partial or unsupported.</p>
     *
     * @return true when the outcome is a decision
     */
    public boolean decides() {
        return this == VALIDATED || this == INCORRECT || this == PARTIAL || this == QUALIFIED
                || this == UNSUPPORTED;
    }

    /**
     * True when this outcome is a validated result.
     *
     * @return true when the result was correct and validated
     */
    public boolean validated() {
        return this == VALIDATED;
    }

    /**
     * True when the call returned an answer of some kind.
     *
     * <p><b>What this is for.</b> The measurement table of the ledger counts two things: the
     * calls that came back with something, and the calls that did not. A call that threw, ran
     * out of its budget, was refused or was cancelled came back with nothing — counting it as
     * a completion is how a method that times out every time comes to look as though it works
     * every time. An abstention <i>is</i> a completion: the method answered, and what it
     * answered was "I do not know".</p>
     *
     * @return true when the call returned
     */
    public boolean completed() {
        return this != TECHNICAL_FAILURE && this != TIMEOUT && this != REFUSED
                && this != CANCELLED;
    }

    /**
     * True when this outcome says nothing at all about the method's reasoning.
     *
     * <p>These are the calls that are counted and reported and never enter the estimate's
     * denominator: the call failed, timed out, was refused, was cancelled, or returned
     * without anything confirming it.</p>
     *
     * @return true when the outcome is not evidence
     */
    public boolean notEvidence() {
        return !decides() && this != ABSTAINED;
    }
}
