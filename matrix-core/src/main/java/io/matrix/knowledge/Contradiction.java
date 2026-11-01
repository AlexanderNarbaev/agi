package io.matrix.knowledge;

/**
 * A recorded conflict between two claims (RECON-W41).
 *
 * <p>The mission's §5.3: "Do not use majority voting as a universal truth
 * mechanism. Record why one claim currently has stronger support than
 * another. Where a conflict cannot be resolved, preserve the uncertainty."
 *
 * <p>So a contradiction is a <b>record about two claims</b>, not a verdict
 * about either of them. Nothing is deleted, demoted or promoted because of
 * a conflict, and the number of records that agree with one side is
 * deliberately not stored in any field the store can read.
 */
public record Contradiction(
        String id,
        String workspaceId,
        String claimAId,
        String claimBId,
        Kind kind,
        String detectedBy,
        String recordedAt) {

    /** What kind of conflict was found. */
    public enum Kind {
        /** The same subject, the same conditions, opposite polarity. */
        OPPOSITE_POLARITY,
        /** The same subject, the same conditions, different figures. */
        DIFFERENT_VALUE,
        /** Two claims that cannot both hold under the same conditions. */
        INCOMPATIBLE_SCOPE
    }

    public Contradiction {
        if (kind == null) {
            kind = Kind.OPPOSITE_POLARITY;
        }
    }
}
