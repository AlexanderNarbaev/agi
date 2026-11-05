package io.matrix.knowledge;

/**
 * W45.2 — what a recoverable snapshot of a workspace holds, and what it is for.
 *
 * <p><b>Why the snapshot exists.</b> The owner's decision of the W45.2 wave: a destructive
 * workspace reload fails closed and leaves a recoverable snapshot. Before it, the reload
 * deleted the workspace's rows and inserted the package's rows in one transaction, and the
 * contradiction history, the evidence and the revisions went with it — a decision that no
 * reader could see and no user could take back. A snapshot is what makes the replacement
 * recoverable: it holds the content as it was BEFORE the replacement, durably, and it can be
 * inspected long after the replacement without being restored.</p>
 *
 * <p><b>What a snapshot is not.</b> It is not a backup of the whole database: it holds the
 * content of ONE workspace, and it holds exactly the records that a replacement of that
 * workspace erases. The records a replacement does not erase — the assessment items, the
 * asked questions, the learner's own history — are not in the snapshot, because they are
 * not at risk from a replacement and duplicating them would create a second source of truth
 * for them.</p>
 *
 * <p><b>W47 — the cases are in the snapshot now.</b> The owner's decision of 2026-11-05
 * (OD-025) put the epistemic case, its occurrences, its disposition and its adjudication
 * history INTO the erasure set: a replacement erases the case together with its history and
 * its evidence. A snapshot that did not carry them would have left the only recoverable copy
 * of the case history behind at the moment it was destroyed, so the payload carries them
 * and the counts below say how many there are.</p>
 *
 * <p><b>Why the counts are here.</b> A snapshot is meant to be INSPECTED before anybody
 * decides to restore it: which workspace, when, what kind of operation took it, and how much
 * it holds. The counts are read from the snapshot's own content, so a reader never has to
 * restore anything to find out what a snapshot is worth.</p>
 *
 * @param id                 the identifier of the snapshot; unique
 * @param workspaceId        the workspace the snapshot holds
 * @param createdAt          when the snapshot was taken
 * @param kind               what the snapshot was taken for
 * @param conceptCount       how many concepts it holds
 * @param claimCount         how many claims it holds
 * @param sourceCount        how many sources it holds
 * @param evidenceCount      how many evidence links it holds
 * @param representationCount how many representations it holds
 * @param revisionCount      how many revisions it holds
 * @param contradictionCount how many recorded conflicts it holds
 * @param derivationCount    how many derivations it holds
 * @param prerequisiteCount  how many prerequisites it holds
 * @param goalCount          how many goals it holds
 * @param caseCount          how many epistemic cases it holds
 * @param occurrenceCount    how many observation occurrences it holds
 * @param dispositionCount   how many observation dispositions it holds
 * @param adjudicationCount  how many observation adjudications it holds
 */
public record WorkspaceSnapshot(
        String id,
        String workspaceId,
        String createdAt,
        Kind kind,
        int conceptCount,
        int claimCount,
        int sourceCount,
        int evidenceCount,
        int representationCount,
        int revisionCount,
        int contradictionCount,
        int derivationCount,
        int prerequisiteCount,
        int goalCount,
        int caseCount,
        int occurrenceCount,
        int dispositionCount,
        int adjudicationCount) {

    /**
     * The counts of the knowledge content alone.
     *
     * <p>A snapshot taken before the W47 wave holds no case history, so the four counts of
     * the observation tables are zero. The form is kept so that a reader of the older
     * shape is not broken by the newer one.</p>
     */
    public WorkspaceSnapshot(String id, String workspaceId, String createdAt, Kind kind,
            int conceptCount, int claimCount, int sourceCount, int evidenceCount,
            int representationCount, int revisionCount, int contradictionCount,
            int derivationCount, int prerequisiteCount, int goalCount) {
        this(id, workspaceId, createdAt, kind, conceptCount, claimCount, sourceCount, evidenceCount,
                representationCount, revisionCount, contradictionCount, derivationCount,
                prerequisiteCount, goalCount, 0, 0, 0, 0);
    }

    /** Why the snapshot was taken. */
    public enum Kind {
        /** The workspace was about to be replaced by the bundled package. */
        LOAD,
        /** The workspace was about to be restored from an earlier snapshot. */
        RESTORE,
        /** The snapshot was taken on request, without anything being replaced. */
        MANUAL
    }

    public WorkspaceSnapshot {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a snapshot id must not be blank");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("a snapshot must name the workspace it holds");
        }
        if (createdAt == null || createdAt.isBlank()) {
            throw new IllegalArgumentException("a snapshot must say when it was taken");
        }
        if (kind == null) {
            throw new IllegalArgumentException("a snapshot must say why it was taken");
        }
    }

    /**
     * Whether the snapshot holds anything at all.
     *
     * <p>A snapshot of an empty workspace is a record that the workspace was empty when it was
     * replaced — which is worth keeping, because it says that the replacement destroyed
     * nothing.</p>
     *
     * @return true when the snapshot holds no content
     */
    public boolean empty() {
        return conceptCount == 0 && claimCount == 0 && sourceCount == 0 && evidenceCount == 0
                && representationCount == 0 && revisionCount == 0 && contradictionCount == 0
                && derivationCount == 0 && prerequisiteCount == 0 && goalCount == 0;
    }
}
