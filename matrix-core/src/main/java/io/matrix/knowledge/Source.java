package io.matrix.knowledge;

/**
 * A document, dataset, observation, experiment, authoritative reference or
 * explicitly identified human input (owner directive §14).
 *
 * <p>{@code locator} is where the material actually is — a path, a URL or a
 * human description. {@code authority} and {@code freshness} are separate
 * dimensions: a textbook is authoritative and may be old; an observation is
 * fresh and may be weak.
 */
public record Source(
        String id,
        String workspaceId,
        SourceKind kind,
        String title,
        String locator,
        int authority,
        int freshness,
        String excerpt,
        String addedAt) {

    public Source {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("source id must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("source kind must not be null");
        }
        authority = Math.max(0, Math.min(100, authority));
        freshness = Math.max(0, Math.min(100, freshness));
    }
}
