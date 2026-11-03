package io.matrix.cognitive.algorithms;

import java.util.Set;

/**
 * W44.2 — a validated, reusable procedure: an expensive reasoning result turned into a cheaper
 * path, with the conditions under which it may be used.
 *
 * <p>The W44 directive §22 is explicit about the sequence — solve the problem expensively, record
 * the reference result, generate a cheaper candidate, validate it against the reference on
 * held-out cases, promote it only if it preserves the required quality, store the applicability
 * conditions, monitor it, and invalidate it after drift. This record is what that sequence
 * leaves behind.</p>
 *
 * <p>Three types of reuse must not be confused, and this record only describes the middle one:
 * a cached ANSWER is reusable only for equivalent conditions; a learned MODEL is reusable only
 * within its measured distribution; a cached PROCEDURE is reusable only inside its verified
 * applicability domain. The {@code requiredFacts} field is that domain, and it is checked before
 * the procedure is used.</p>
 *
 * <p>{@code referenceAlgorithmId} is the fallback. A procedure without a reference to fall back
 * on is a trap: when the domain no longer holds, there would be nothing left to do.</p>
 *
 * <p>{@code validationAgreement} is the measured agreement with the reference, with the case
 * count attached. A procedure with no validation has {@code validationCases == 0} and is not
 * promotable — the promotion rule lives with the validator, not in this record.</p>
 *
 * <p><b>W45.0: {@code validationKind} says what the agreement actually shows.</b> The W44
 * record called every agreement a held-out validation. It was not one: the kernel validated the
 * fast path against the reference on the observations the model had already been built from.
 * {@link ValidationKind#IN_SAMPLE} is a consistency check — the cheap path computes what the
 * expensive path computes — and {@link ValidationKind#INDEPENDENT} is a validation. Only the
 * second may be called one, and {@link #validated()} is the one place that decides.</p>
 */
public record Procedure(
        String id,
        String name,
        String problemFamily,
        String version,
        String applicability,
        Set<String> requiredFacts,
        String referenceAlgorithmId,
        int validationCases,
        double validationAgreement,
        int useCount,
        int failureCount,
        String promotedAt,
        String invalidatedAt,
        ValidationKind validationKind) {

    public Procedure {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("procedure id must not be blank");
        }
        if (problemFamily == null || problemFamily.isBlank()) {
            throw new IllegalArgumentException("procedure problem family must not be blank");
        }
        if (referenceAlgorithmId == null || referenceAlgorithmId.isBlank()) {
            throw new IllegalArgumentException("procedure must name a reference algorithm");
        }
        requiredFacts = requiredFacts == null ? Set.of() : Set.copyOf(requiredFacts);
        validationKind = validationKind == null
                ? ValidationKind.IN_SAMPLE : validationKind;
    }

    /** True when the procedure may still be used. */
    public boolean active() {
        return invalidatedAt == null || invalidatedAt.isBlank();
    }

    /**
     * Whether this procedure rests on genuinely independent evidence.
     *
     * <p>This is the only place in the codebase that decides what may be called a validated
     * procedure. A record whose agreement was computed over the model's own data is a
     * consistency check, and it is labelled as such.</p>
     *
     * @return true only for independently validated procedures
     */
    public boolean validated() {
        return validationKind != null && validationKind.isValidation();
    }

    /** The same procedure, invalidated at a moment. */
    public Procedure invalidatedAt(String moment) {
        return new Procedure(id, name, problemFamily, version, applicability, requiredFacts,
                referenceAlgorithmId, validationCases, validationAgreement,
                useCount, failureCount, promotedAt, moment, validationKind);
    }

    /** True when the given facts satisfy the procedure's applicability domain. */
    public boolean applicable(Set<String> facts) {
        return facts != null && facts.containsAll(requiredFacts);
    }

    /** The same procedure after one use. */
    public Procedure used(boolean succeeded) {
        return new Procedure(id, name, problemFamily, version, applicability, requiredFacts,
                referenceAlgorithmId, validationCases, validationAgreement,
                useCount + 1, failureCount + (succeeded ? 0 : 1), promotedAt, invalidatedAt,
                validationKind);
    }
}
