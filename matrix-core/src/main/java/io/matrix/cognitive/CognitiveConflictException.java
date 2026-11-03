package io.matrix.cognitive;

/**
 * W45.0 — the explicit refusal to overwrite something that must not be overwritten.
 *
 * <p>W45 Priority B replaced every {@code INSERT OR REPLACE} in the cognitive store with an
 * explicit write policy, and the policy has three outcomes for a record that already exists:</p>
 * <ul>
 *   <li>the same id with the SAME content — an idempotent no-op: the write succeeds and nothing
 *       changes;</li>
 *   <li>the same id with DIFFERENT content — a CONFLICT: this exception, never a silent
 *       overwrite;</li>
 *   <li>a different id — an ordinary insert.</li>
 * </ul>
 *
 * <p>The conflict is an exception and not a flag, because a flag can be ignored. The store
 * raises it wherever a silent overwrite used to happen, and the HTTP surface turns it into a
 * {@code 409 Conflict} with an explanation.</p>
 *
 * <p>Three kinds exist, because they mean different things to a caller:</p>
 * <ul>
 *   <li>{@link Kind#IMMUTABLE_RECORD} — a fact of an episode was about to be rewritten;</li>
 *   <li>{@link Kind#EPISODE_STATE} — the lifecycle of an episode was violated (an episode that
 *       was opened twice, or closed twice with different instants);</li>
 *   <li>{@link Kind#IDEMPOTENCY_KEY} — one idempotency key was delivered with two different
 *       payloads.</li>
 * </ul>
 */
public class CognitiveConflictException extends RuntimeException {

    /** What kind of conflict this is. */
    public enum Kind {
        /** An immutable fact was about to be overwritten. */
        IMMUTABLE_RECORD,
        /** The open/close lifecycle of an episode was violated. */
        EPISODE_STATE,
        /** One idempotency key was delivered with two different payloads. */
        IDEMPOTENCY_KEY
    }

    private final Kind kind;

    /**
     * A conflict.
     *
     * @param kind    what kind of conflict this is
     * @param message what was refused, in words a caller can act on
     */
    public CognitiveConflictException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    /** What kind of conflict this is. */
    public Kind kind() {
        return kind;
    }
}
