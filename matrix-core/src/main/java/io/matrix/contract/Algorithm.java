package io.matrix.contract;

import java.util.List;

/**
 * W51 — an algorithm the system may choose to use, with what is known about it.
 *
 * <p><b>An algorithm is not a reasoning engine.</b> The W51 requirement names the failure
 * directly: "Do not label a component as a reasoning engine if it is only a retrieval helper."
 * This record therefore says what the algorithm <i>does</i> — its family, the problem it
 * solves — and what is <i>measured</i> about it — the accuracy evidence and its denominator,
 * the cost basis. A retrieval helper declares a retrieval family and cannot claim a reasoning
 * family, whatever its class is called.</p>
 *
 * <p><b>The accuracy evidence carries its denominator.</b> A score without the number of cases
 * behind it is a number, not evidence. The method selector refuses to compare methods whose
 * evidence has no denominator, and this record makes the omission impossible to hide.</p>
 *
 * <p><b>The cost basis is stated.</b> {@link CostBasis#MEASURED} means the cost was measured
 * for this algorithm id; {@link CostBasis#DECLARED} means the declared complexity was used as
 * a placeholder. Mixing the two would make a nanosecond count comparable to a big-O label,
 * which it is not.</p>
 *
 * @param id                 the identity of the algorithm
 * @param version            the revision number of the algorithm
 * @param family             the family of problems the algorithm solves
 * @param description        what the algorithm does, in one honest sentence
 * @param requiredPreconditions the facts that must hold for the algorithm to be applicable
 * @param accuracy           the measured accuracy, in {@code [0,1]}
 * @param accuracyDenominator the number of cases the accuracy was measured over
 * @param costBasis          where the cost estimate came from
 * @param costDescription    the cost, in the terms the basis implies
 * @param fallbackAlgorithmId the algorithm to use when this one cannot run, or {@code null}
 * @param procedureId        the procedure that implements it, when it has one
 */
public record Algorithm(
        String id,
        int version,
        String family,
        String description,
        List<String> requiredPreconditions,
        double accuracy,
        int accuracyDenominator,
        CostBasis costBasis,
        String costDescription,
        String fallbackAlgorithmId,
        String procedureId) {

    /** Where the cost estimate of an algorithm comes from. */
    public enum CostBasis {
        /** The cost was measured for this algorithm id. */
        MEASURED,
        /** The declared complexity was used; it is a placeholder, not a measurement. */
        DECLARED
    }

    public Algorithm {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("algorithm id must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("algorithm version must be at least one");
        }
        if (family == null || family.isBlank()) {
            throw new IllegalArgumentException("algorithm family must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("algorithm description must not be blank");
        }
        requiredPreconditions = requiredPreconditions == null ? List.of() : List.copyOf(requiredPreconditions);
        if (Double.isNaN(accuracy) || accuracy < 0.0 || accuracy > 1.0) {
            throw new IllegalArgumentException("algorithm accuracy must be within [0,1]");
        }
        if (accuracyDenominator < 0) {
            throw new IllegalArgumentException("algorithm accuracy denominator must not be negative");
        }
        if (costBasis == null) {
            throw new IllegalArgumentException("algorithm cost basis must not be null");
        }
    }

    /**
     * True when the accuracy claim rests on at least one measured case.
     *
     * <p>A method with no denominator has no evidence, and the selector scores it as having
     * none rather than as being perfect or as being useless.</p>
     */
    public boolean hasAccuracyEvidence() {
        return accuracyDenominator > 0;
    }

    /** True when the algorithm is backed by a validated, reusable procedure. */
    public boolean procedureBacked() {
        return procedureId != null && !procedureId.isBlank();
    }

    @Override
    public String toString() {
        return "Algorithm[" + id + " v" + version + " " + family + "]";
    }
}
