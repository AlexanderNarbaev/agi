package io.matrix.knowledge;

/**
 * W45.1 — the explicit refusal to overwrite something that must not be overwritten.
 *
 * <p>The knowledge store's observation records have three outcomes when a record that already
 * exists is written again:</p>
 * <ul>
 *   <li>the same id with the SAME content — an idempotent no-op: the write succeeds and
 *       nothing changes;</li>
 *   <li>the same id with DIFFERENT content — a CONFLICT: this exception, never a silent
 *       overwrite;</li>
 *   <li>a different id — an ordinary insert.</li>
 * </ul>
 *
 * <p>The conflict is an exception and not a flag, because a flag can be ignored. The W45.0
 * wave wrote the disposition of a case with {@code INSERT OR REPLACE}, so a re-observation
 * erased the adjudication a review had taken, and nothing anywhere said so: the write
 * succeeded, the record looked right, and the decision was gone. A refusal that can be
 * swallowed is not a refusal.</p>
 *
 * <p>Three kinds exist, because they mean different things to a caller:</p>
 * <ul>
 *   <li>{@link Kind#DECISION} — one decision id was delivered with two different contents, or
 *       two decisions raced over the same case;</li>
 *   <li>{@link Kind#OCCURRENCE} — one occurrence id was delivered with two different
 *       contents;</li>
 *   <li>{@link Kind#TRANSITION} — a disposition was asked to move somewhere the state machine
 *       does not allow.</li>
 * </ul>
 */
public class ObservationConflictException extends RuntimeException {

    /** What kind of conflict this is. */
    public enum Kind {
        /** A decision id was reused with different content, or two decisions raced. */
        DECISION,
        /** An occurrence id was reused with different content. */
        OCCURRENCE,
        /** A disposition was asked to move somewhere the state machine does not allow. */
        TRANSITION
    }

    private final Kind kind;

    /**
     * A conflict.
     *
     * @param kind    what kind of conflict this is
     * @param message what was refused, in words a caller can act on
     */
    public ObservationConflictException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    /** What kind of conflict this is. */
    public Kind kind() {
        return kind;
    }
}
