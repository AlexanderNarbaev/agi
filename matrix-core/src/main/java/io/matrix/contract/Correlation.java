package io.matrix.contract;

/**
 * W51 — the causality metadata every message carries.
 *
 * <p><b>What this replaces.</b> Nothing carried it. A trace across two processes had no way to
 * say which command caused which event, and the W51 requirement for "a machine-readable trace
 * that connects the observation, decision, method, prediction, action, outcome, evidence and
 * learning result" is unimplementable without it: a trace is a chain of references, and the
 * references have to be explicit.</p>
 *
 * <p><b>Four identifiers, four namespaces.</b></p>
 * <ol>
 *   <li>{@code correlationId} — the identity of the logical REQUEST. Every message produced by
 *       handling that request carries it, whether it crossed a process boundary or not.</li>
 *   <li>{@code causationId} — the identifier of the message that caused THIS one. It is
 *       {@code null} for a root request and set for everything downstream.</li>
 *   <li>{@code episodeId} — the identity of the cognitive episode the message belongs to. The
 *       kernel already allocates these; the envelope carries them rather than inventing a
 *       second numbering.</li>
 *   <li>{@code idempotencyKey} — the key a retried command carries, so that a delivery that
 *       arrives twice is recognised as the same command rather than performed twice.</li>
 * </ol>
 *
 * <p><b>A correlation id is not an identity of content.</b> Two different commands may share a
 * correlation id — that is what correlation means — but two different commands may not share an
 * idempotency key: the second one is a conflict, not a retry, and the runtime must say so
 * rather than silently perform it.</p>
 *
 * @param correlationId  the identity of the logical request
 * @param causationId   the identifier of the message that caused this one, or {@code null}
 * @param episodeId     the cognitive episode the message belongs to, or {@code null}
 * @param idempotencyKey the key that makes a repeated delivery recognisable, or {@code null}
 */
public record Correlation(
        String correlationId,
        String causationId,
        String episodeId,
        String idempotencyKey) {

    /** A correlation with only a request identity. */
    public static Correlation ofRequest(String correlationId) {
        return new Correlation(correlationId, null, null, null);
    }

    public Correlation {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlation id must not be blank");
        }
    }

    /** True when the message carries an idempotency key. */
    public boolean idempotent() {
        return idempotencyKey != null && !idempotencyKey.isBlank();
    }

    /** True when the message was caused by another one. */
    public boolean caused() {
        return causationId != null && !causationId.isBlank();
    }

    /**
     * The correlation of a message caused by this one.
     *
     * @param causationId the identifier of the causing message
     * @return a correlation that keeps the request identity and records the cause
     */
    public Correlation causedBy(String causationId) {
        return new Correlation(correlationId, causationId, episodeId, idempotencyKey);
    }

    /**
     * The correlation of a message belonging to an episode.
     *
     * @param episodeId the episode identity
     * @return a correlation that keeps the request identity and records the episode
     */
    public Correlation inEpisode(String episodeId) {
        return new Correlation(correlationId, causationId, episodeId, idempotencyKey);
    }

    @Override
    public String toString() {
        return "Correlation[req=" + correlationId
                + (causationId == null ? "" : " caused-by=" + causationId)
                + (episodeId == null ? "" : " episode=" + episodeId)
                + (idempotencyKey == null ? "" : " key=" + idempotencyKey) + "]";
    }
}
