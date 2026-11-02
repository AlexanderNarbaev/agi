package io.matrix.knowledge;

/**
 * W44 — a first-class case: one observation, the conditions it was made under, and what was
 * seen.
 *
 * <p>This is the model that {@code ADR-W43-01} §4.5 planned and that
 * {@code RECON-W43-STATUS.md} recorded as {@code PLANNED} pending the owner's schema-16
 * decision. The W44 directive is that decision: the case model has to be reconciled with the
 * implementation, and an observation that carries its conditions is what lets the cognitive
 * kernel tell a prediction from an observation.</p>
 *
 * <p>Before this record existed, an observation was a {@link Source} of kind
 * {@code OBSERVATION} whose {@code locator} held the conditions, and a counterexample was an
 * {@link Evidence} row with relation {@code CONTRADICTS}. That worked, and it kept working, but
 * the conditions had no home of their own: they were a string in a field whose documented
 * purpose was a locator. A case gives the conditions a name.</p>
 *
 * <p>The fields are deliberately small. {@code conditions} holds the conditions that were held
 * fixed while the outcome was observed, stored VERBATIM — the text the observer wrote, not a
 * re-serialisation of it, so that a reader can reconstruct the observation without guessing
 * what the string meant. {@code outcome} holds what was seen, in the same way.</p>
 *
 * <p>{@code origin} says how the case came to exist. It is what separates an observation the
 * system made itself from a value somebody reported, from a value the system derived, and from
 * a deliberately inconsistent value injected to test whether the system notices.</p>
 */
public record Case(
        String id,
        String workspaceId,
        String conceptId,
        String statement,
        String conditions,
        String outcome,
        String sourceId,
        Origin origin,
        String observedAt) {

    /** How the case came to exist. */
    public enum Origin {
        /** The system observed it in an environment it controls. */
        OBSERVED,
        /** Somebody reported it. A report is not an observation by the system. */
        REPORTED,
        /** The system derived it from other cases. */
        DERIVED,
        /** Deliberately inconsistent — a fault injected to test whether the system notices. */
        ADVERSARIAL
    }

    public Case {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("case id must not be blank");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("case workspace id must not be blank");
        }
        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("case statement must not be blank");
        }
        if (origin == null) {
            throw new IllegalArgumentException("case origin must not be null");
        }
        conditions = conditions == null ? "{}" : conditions;
        outcome = outcome == null ? "{}" : outcome;
    }
}
