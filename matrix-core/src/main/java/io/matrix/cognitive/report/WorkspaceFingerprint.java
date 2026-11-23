package io.matrix.cognitive.report;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeSnapshot;

/**
 * P2 — the fingerprint of a workspace's content.
 *
 * <p><b>What it is for.</b> The replay may only serve a retained answer when the workspace
 * has not changed since the journey that produced it. The fingerprint is how that is
 * established: it covers the fields of every claim that can change WHAT the workspace
 * would answer — the claim's identifier, the concept it belongs to, its proposition, its
 * scope, its context, its status and its version.</p>
 *
 * <p><b>Why those fields.</b> A claim's scope and context are part of what the claim says:
 * a claim whose scope narrowed, or whose context moved to another document, answers a
 * different question even when its proposition did not change. A fingerprint that covered
 * only the proposition would let the replay serve an answer the workspace would no longer
 * give.</p>
 *
 * <p><b>Why the fields are read one by one.</b> A fingerprint built by reflection over the
 * claim's components would widen silently when a component is added, and the widening
 * would be invisible in the trace. Here the list of fields is written down, and a field
 * that is not written down does not move the fingerprint.</p>
 */
public final class WorkspaceFingerprint {

    /**
     * The separator between the fields of one claim.
     *
     * <p>CONVENTION: a character that cannot occur inside a field's value, so that two
     * different claims cannot produce the same material by accident.</p>
     */
    public static final String FIELD_SEPARATOR = "|";

    /** The separator between two claims. */
    public static final String CLAIM_SEPARATOR = "\n";

    private WorkspaceFingerprint() {
    }

    /**
     * The fingerprint of a workspace's claims.
     *
     * @param snapshot the workspace as it is now
     * @return the fingerprint
     */
    public static String of(KnowledgeSnapshot snapshot) {
        return of(snapshot, null);
    }

    public static String of(KnowledgeSnapshot snapshot, String historicalEventId) {
        StringBuilder material = new StringBuilder();
        for (Claim claim : snapshot.claims()) {
            material.append(claim.id())
                    .append(FIELD_SEPARATOR)
                    .append(claim.conceptId())
                    .append(FIELD_SEPARATOR)
                    .append(claim.proposition())
                    .append(FIELD_SEPARATOR)
                    .append(claim.scope())
                    .append(FIELD_SEPARATOR)
                    .append(claim.context())
                    .append(FIELD_SEPARATOR)
                    .append(claim.status())
                    .append(FIELD_SEPARATOR)
                    .append(claim.version())
                    .append(CLAIM_SEPARATOR);
        }
        if (!snapshot.sourceRevisionSelections().isEmpty()) {
            appendFields(material, "source-revision-view-v2", snapshot.workspaceId(),
                    historicalEventId == null ? "CURRENT" : "HISTORICAL_DOCUMENT", historicalEventId);
            snapshot.sourceRevisionSelections().stream()
                    .sorted(java.util.Comparator.comparing(io.matrix.knowledge.SourceRevisionSelection::documentKey))
                    .forEach(event -> appendFields(material, event.documentKey(), event.eventId(),
                            event.sourceId(), event.contentHash()));
            snapshot.sources().stream().sorted(java.util.Comparator.comparing(io.matrix.knowledge.Source::id))
                    .forEach(source -> appendFields(material, source.id(), source.contentHash()));
            snapshot.evidence().stream().sorted(java.util.Comparator.comparing(io.matrix.knowledge.Evidence::id))
                    .forEach(row -> appendFields(material, row.id(), row.claimId(),
                            row.sourceId(), row.relation().name(), row.excerpt()));
        }
        return ArtifactObserver.sha256(material.toString());
    }

    private static void appendFields(StringBuilder material, String... fields) {
        for (String field : fields) {
            material.append(field == null ? -1 : field.length()).append(':');
            if (field != null) {
                material.append(field);
            }
        }
    }
}
