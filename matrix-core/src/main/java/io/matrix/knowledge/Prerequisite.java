package io.matrix.knowledge;

/**
 * A concept, skill or fact needed for reliable use of another capability
 * (owner directive §14).
 */
public record Prerequisite(
        String workspaceId,
        String conceptId,
        String requiresConceptId,
        boolean required,
        String note) {

    public Prerequisite {
        if (conceptId == null || conceptId.isBlank()
                || requiresConceptId == null || requiresConceptId.isBlank()) {
            throw new IllegalArgumentException("prerequisite ids must not be blank");
        }
        if (conceptId.equals(requiresConceptId)) {
            throw new IllegalArgumentException("a concept cannot require itself");
        }
    }
}
