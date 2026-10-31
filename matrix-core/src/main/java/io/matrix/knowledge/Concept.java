package io.matrix.knowledge;

/**
 * A stable identity for an idea, object, mechanism or domain
 * (owner directive §14).
 *
 * <p>The identity is stable across revisions: refining a concept creates a
 * new REPRESENTATION of the same concept, never a new concept.
 */
public record Concept(
        String id,
        String workspaceId,
        String name,
        String definition,
        String domain,
        String createdAt) {

    public Concept {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("concept id must not be blank");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspace id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("concept name must not be blank");
        }
    }
}
