package io.matrix.knowledge;

/**
 * An individual proposition about a concept (owner directive §14).
 *
 * <p>The claim carries its own scope, because the same proposition can hold
 * in one context and fail in another — "plants release oxygen during the day"
 * is true under light and false in darkness, and the claim must be able to
 * say so without being refuted.
 */
public record Claim(
        String id,
        String workspaceId,
        String conceptId,
        String proposition,
        String scope,
        String context,
        ClaimStatus status,
        double confidence,
        int version,
        String introducedBySourceId,
        String createdAt,
        String updatedAt) {

    public Claim {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("claim id must not be blank");
        }
        if (conceptId == null || conceptId.isBlank()) {
            throw new IllegalArgumentException("claim concept id must not be blank");
        }
        if (proposition == null || proposition.isBlank()) {
            throw new IllegalArgumentException("claim proposition must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("claim status must not be null");
        }
        if (confidence < 0.0 || confidence > 1.0 || Double.isNaN(confidence)) {
            throw new IllegalArgumentException("claim confidence must be within [0,1]");
        }
    }

    /** True when the claim's status asserts it as established. */
    public boolean isEstablished() {
        return status == ClaimStatus.SUPPORTED
                || status == ClaimStatus.VERIFIED;
    }
}
