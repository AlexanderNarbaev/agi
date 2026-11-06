package io.matrix.knowledge;

/**
 * W48-01 — a restore that would destroy what it cannot bring back.
 *
 * <p>A restore erases the workspace and then writes the payload back. When the payload
 * was written by a build that did not capture a family the target holds, the erase
 * would remove rows the payload cannot restore: the observation history, or the
 * learning state, or both.</p>
 *
 * <p>This exception is that refusal. It is thrown BEFORE any destructive change, so the
 * database is exactly as it was when the restore was attempted.</p>
 *
 * <p>The identifier is stable and is what a caller should match on:
 * {@code INCOMPLETE_LEGACY_SNAPSHOT}. It is also the first line of the message, so a
 * caller that only logs the message still carries the identifier.</p>
 */
public class IncompatibleSnapshotException extends IllegalStateException {

    /** The stable identifier of this refusal. */
    public static final String IDENTIFIER = "INCOMPLETE_LEGACY_SNAPSHOT";

    private static final long serialVersionUID = 1L;

    private final String snapshotId;
    private final SnapshotCoverage coverage;
    private final java.util.List<String> missingFamilies;

    /**
     * @param snapshotId      the snapshot that was refused
     * @param coverage        what the payload carries
     * @param missingFamilies the families the payload does not carry
     * @param detail          what the target holds and would have lost
     */
    public IncompatibleSnapshotException(String snapshotId, SnapshotCoverage coverage,
            java.util.List<String> missingFamilies, String detail) {
        super(IDENTIFIER + ": the snapshot " + snapshotId + " carries "
                + coverage.name().toLowerCase(java.util.Locale.ROOT)
                + " content and not " + String.join(" nor ", missingFamilies)
                + ". Restoring it would destroy what it cannot bring back. " + detail);
        this.snapshotId = snapshotId;
        this.coverage = coverage;
        this.missingFamilies = java.util.List.copyOf(missingFamilies);
    }

    /** The snapshot that was refused. */
    public String snapshotId() {
        return snapshotId;
    }

    /** What the payload carries. */
    public SnapshotCoverage coverage() {
        return coverage;
    }

    /** The families the payload does not carry. */
    public java.util.List<String> missingFamilies() {
        return missingFamilies;
    }
}
