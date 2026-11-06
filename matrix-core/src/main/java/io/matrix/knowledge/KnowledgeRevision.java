package io.matrix.knowledge;

import java.util.List;

/**
 * An immutable record of how a previous representation changed
 * (owner directive §13, §14).
 *
 * <p>Historical revisions are never deleted. The record says WHAT changed,
 * WHICH earlier assumptions remain valid, WHICH simplifications no longer
 * apply, WHICH new cases the refined model explains, and WHICH evidence
 * supports the change — so a reader can see the reasoning, not just the
 * outcome.
 */
public record KnowledgeRevision(
        String id,
        String workspaceId,
        String targetType,
        String targetId,
        RevisionKind kind,
        String summary,
        List<String> addedFacts,
        List<String> stillValidAssumptions,
        List<String> noLongerValidAssumptions,
        List<String> newlyExplainedCases,
        List<String> evidenceIds,
        String recordedAt) {

    public KnowledgeRevision {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("revision id must not be blank");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("revision target id must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("revision kind must not be null");
        }
        addedFacts = List.copyOf(addedFacts == null ? List.of() : addedFacts);
        stillValidAssumptions = List.copyOf(stillValidAssumptions == null ? List.of() : stillValidAssumptions);
        noLongerValidAssumptions = List.copyOf(noLongerValidAssumptions == null ? List.of() : noLongerValidAssumptions);
        newlyExplainedCases = List.copyOf(newlyExplainedCases == null ? List.of() : newlyExplainedCases);
        evidenceIds = List.copyOf(evidenceIds == null ? List.of() : evidenceIds);
    }
}
