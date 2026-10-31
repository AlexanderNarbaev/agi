package io.matrix.knowledge;

import java.util.List;

/**
 * A set of incremental changes to a workspace (owner directive §13, §20
 * KnowledgeRefiner).
 *
 * <p>Importing a package REPLACES a workspace; changing what is known does
 * not. This record carries the difference: the claims that were added or
 * changed, the evidence that supports them, and the revisions that explain
 * what changed and why.
 */
public record KnowledgeChanges(
        List<Source> sources,
        List<Claim> claims,
        List<Evidence> evidence,
        List<KnowledgeRevision> revisions) {

    public KnowledgeChanges {
        sources = List.copyOf(sources == null ? List.of() : sources);
        claims = List.copyOf(claims == null ? List.of() : claims);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        revisions = List.copyOf(revisions == null ? List.of() : revisions);
    }

    /** An empty set of changes. */
    public static KnowledgeChanges none() {
        return new KnowledgeChanges(List.of(), List.of(), List.of(), List.of());
    }
}
