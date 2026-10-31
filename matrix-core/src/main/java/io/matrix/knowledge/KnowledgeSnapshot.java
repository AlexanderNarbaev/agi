package io.matrix.knowledge;

import java.util.List;

/**
 * The complete content of one workspace, in one immutable aggregate.
 *
 * <p>This is the unit of import, export and persistence. A snapshot is
 * self-contained: every evidence record names a claim and a source that are
 * inside it, every representation names a concept that is inside it, and the
 * refinement chain points at representations that are inside it. Importing a
 * snapshot is therefore an atomic operation — either the whole workspace
 * arrives or none of it does.
 *
 * <p>Identifiers are DETERMINISTIC: importing the same pack twice produces
 * the same identifiers, so re-importing is idempotent rather than
 * duplicative. That is a requirement of the product, not a convenience: a
 * user who imports a pack twice must not end up with two copies of every
 * concept.
 */
public record KnowledgeSnapshot(
        String workspaceId,
        String workspaceName,
        List<Concept> concepts,
        List<Source> sources,
        List<Claim> claims,
        List<Evidence> evidence,
        List<Representation> representations,
        List<Prerequisite> prerequisites,
        List<KnowledgeRevision> revisions,
        String exportedAt) {

    public KnowledgeSnapshot {
        concepts = List.copyOf(concepts == null ? List.of() : concepts);
        sources = List.copyOf(sources == null ? List.of() : sources);
        claims = List.copyOf(claims == null ? List.of() : claims);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        representations = List.copyOf(representations == null ? List.of() : representations);
        prerequisites = List.copyOf(prerequisites == null ? List.of() : prerequisites);
        revisions = List.copyOf(revisions == null ? List.of() : revisions);
    }

    /** An empty workspace. */
    public static KnowledgeSnapshot empty(String workspaceId, String workspaceName) {
        return new KnowledgeSnapshot(workspaceId, workspaceName,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
    }
}
