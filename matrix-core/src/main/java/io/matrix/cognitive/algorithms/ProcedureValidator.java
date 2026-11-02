package io.matrix.cognitive.algorithms;

import java.util.List;

/**
 * W44.2 — the rule that decides whether a cheap procedure may be promoted.
 *
 * <p>The rule is deliberately strict, and it is strict in the direction of refusing. A procedure
 * is promoted ONLY when it agrees with the reference method on EVERY held-out case, and only when
 * there are enough of those cases for the agreement to mean something. Two ways of getting this
 * wrong are visible in practice, and both are guarded against here:</p>
 *
 * <ul>
 *   <li><b>Promoting on a handful of cases.</b> One agreeing case is not evidence. The minimum
 *       is a named constant rather than a literal at the call site, so that the threshold is
 *       visible in the code and can be argued about.</li>
 *   <li><b>Promoting a procedure that disagrees somewhere.</b> A procedure that is right 99 % of
 *       the time is still wrong in a way the caller cannot see, and the whole point of the
 *       procedure is that it is replayed WITHOUT being checked. So one disagreement refuses the
 *       promotion, and the reason names the case that disagreed.</li>
 * </ul>
 *
 * <p>The validator does not promote anything itself: it reports, and the store acts. That keeps
 * the rule about validity separate from the storage that holds the result.</p>
 */
public final class ProcedureValidator {

    /**
     * The smallest number of held-out cases a promotion may be decided on.
     *
     * <p>CONVENTION: chosen because five agreeing cases are already more evidence than one, and
     * because a larger corpus would make promotion expensive for a benefit that does not grow
     * with the corpus. The number is named so that it can be argued about rather than buried.</p>
     */
    public static final int MINIMUM_VALIDATION_CASES = 5;

    /**
     * The outcome of validating a candidate against the reference.
     *
     * @param totalCases how many held-out cases were supplied
     * @param agreements how many of them the candidate got right
     * @param agreement the agreement rate, over {@code totalCases}
     * @param accepted whether the candidate may be promoted
     * @param reason why, in words a reader can act on
     */
    public record ValidationOutcome(
            int totalCases, int agreements, double agreement, boolean accepted, String reason) {

        public ValidationOutcome {
            reason = reason == null ? "" : reason;
        }
    }

    private ProcedureValidator() {
    }

    /**
     * Validates a candidate procedure against the reference method.
     *
     * @param candidate the procedure being considered
     * @param cases the held-out cases, each carrying both answers
     * @return the outcome; never null
     */
    public static ValidationOutcome validate(Procedure candidate, List<ValidationCase> cases) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate procedure must not be null");
        }
        List<ValidationCase> heldOut = cases == null ? List.of() : cases;
        if (heldOut.size() < MINIMUM_VALIDATION_CASES) {
            return new ValidationOutcome(heldOut.size(), 0, 0.0, false,
                    "the candidate was validated on " + heldOut.size() + " held-out case(s) and at "
                            + "least " + MINIMUM_VALIDATION_CASES + " are required before a "
                            + "promotion can be decided");
        }
        int agreements = 0;
        ValidationCase firstDisagreement = null;
        for (ValidationCase validationCase : heldOut) {
            if (validationCase.agrees()) {
                agreements++;
            } else if (firstDisagreement == null) {
                firstDisagreement = validationCase;
            }
        }
        double agreement = (double) agreements / (double) heldOut.size();
        if (firstDisagreement != null) {
            return new ValidationOutcome(heldOut.size(), agreements, agreement, false,
                    "the candidate disagreed with the reference on the held-out case '"
                            + firstDisagreement.caseId() + "', so it was not promoted");
        }
        return new ValidationOutcome(heldOut.size(), agreements, agreement, true,
                "the candidate agreed with the reference on all " + heldOut.size()
                        + " held-out case(s)");
    }
}
