package io.matrix.knowledge;

/**
 * An individual proposition about a concept (owner directive §14).
 *
 * <p>The claim carries its own scope, because the same proposition can hold
 * in one context and fail in another — "plants release oxygen during the day"
 * is true under light and false in darkness, and the claim must be able to
 * say so without being refuted.
 *
 * <p>RECON-W41: the claim carries three epistemic facts, kept apart on
 * purpose. {@link ClaimStatus} says which LAYER the claim is in.
 * {@link ClaimWarrant} says what KIND OF THING established it — perception,
 * testimony, inference or comparison. {@link DerivationStatus} says
 * whether an inference was sound. Collapsing them into one label is what
 * lets a system believe things it has not established.
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
        String extractionMethod,
        String validationMethod,
        ClaimWarrant warrant,
        DerivationStatus derivationStatus,
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
        if (warrant == null) {
            warrant = ClaimWarrant.NONE;
        }
        if (derivationStatus == null) {
            derivationStatus = DerivationStatus.NOT_DERIVED;
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
