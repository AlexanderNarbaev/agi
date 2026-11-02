package io.matrix.cognitive.algorithms;

import java.util.Objects;

/**
 * W44.2 — one held-out case in the validation of a candidate procedure.
 *
 * <p>The W44 directive §22 describes the sequence: solve the problem expensively, record the
 * reference result, generate a cheaper candidate, and validate the candidate against the
 * reference on HELD-OUT cases. This record is one such case — the same input, the reference
 * method's answer, and the candidate's answer.</p>
 *
 * <p>Both answers are carried rather than a pass/fail flag, because a case that fails is worth
 * reading: it says WHERE the candidate diverges, and a validator that only counted agreements
 * would throw that away.</p>
 *
 * <p>The comparison is by {@link Objects#equals(Object, Object)}, so the answers have to be
 * value types with meaningful equality. A {@code double[]} would compare by identity and every
 * case would silently disagree, which is why the portfolio's outputs are records of primitives
 * and lists rather than arrays.</p>
 */
public record ValidationCase(
        String caseId,
        Object input,
        Object referenceAnswer,
        Object candidateAnswer) {

    public ValidationCase {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("validation case id must not be blank");
        }
    }

    /** Whether the candidate agreed with the reference on this case. */
    public boolean agrees() {
        return Objects.equals(referenceAnswer, candidateAnswer);
    }
}
