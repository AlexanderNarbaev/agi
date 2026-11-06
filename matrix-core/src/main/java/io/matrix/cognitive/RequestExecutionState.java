package io.matrix.cognitive;

import java.util.EnumSet;
import java.util.Set;

/**
 * W45.1 — the durable lifecycle of ONE logical request.
 *
 * <h2>Why the lifecycle must be explicit</h2>
 * <p>W45.0 had two states: {@code IN_PROGRESS} and {@code COMPLETED}. A process that died with
 * a claim in {@code IN_PROGRESS} left the key claimed forever, and a request that failed after
 * a committed observation DELETED its mapping — so the next delivery started a second episode
 * and committed the same observation twice. Neither behaviour was a decision; both were the
 * absence of one.</p>
 *
 * <p>The states below make the decision explicit. Every transition is a durable write, every
 * terminal state is inspectable, and a request can only be re-executed from a state that
 * PROVES no effect happened.</p>
 *
 * <h2>The rules</h2>
 * <ul>
 *   <li>A request may be executed again only from {@link #FAILED_NO_EFFECT} or
 *       {@link #INTERRUPTED_NO_EFFECT} — the two states that prove nothing was written.</li>
 *   <li>{@link #INTERRUPTED_PARTIAL} and {@link #INDETERMINATE} are NOT re-executable: the work
 *       may already have committed an observation. The caller is told the truth instead.</li>
 *   <li>{@link #COMPLETED} is replayable: the stored outcome is returned and no work is
 *       done.</li>
 * </ul>
 */
public enum RequestExecutionState {

    /** The key is claimed and the identities are allocated; the execution has not started. */
    CLAIMED,

    /** The execution is running. */
    IN_PROGRESS,

    /** The request finished and its outcome is durable. The outcome is replayable. */
    COMPLETED,

    /**
     * The execution failed BEFORE any durable or external effect.
     *
     * <p>This is the only failure state from which a key may be released and the work repeated.
     * It may be established only by proof, never by assumption.</p>
     */
    FAILED_NO_EFFECT,

    /**
     * The process died before any durable effect.
     *
     * <p>The claim is stale, and the retry continues the SAME logical execution — the same
     * episode id, the same signal id.</p>
     */
    INTERRUPTED_NO_EFFECT,

    /**
     * The process died after partial durable work.
     *
     * <p>The episode may be open, a prediction may exist, an observation may be committed. The
     * outcome is not safely repeatable.</p>
     */
    INTERRUPTED_PARTIAL,

    /**
     * An external effect may have happened and its result was not persisted.
     *
     * <p>The only truthful answer is that the outcome is unknown. The micro-world's probe is a
     * pure computation, so it never reaches this state; a future stateful environment
     * would.</p>
     */
    INDETERMINATE,

    /** A recovery was attempted and refused. The request needs a human decision. */
    RECOVERY_REFUSED;

    /** The states from which the work may be executed again. */
    private static final Set<RequestExecutionState> RE_EXECUTABLE = EnumSet.of(
            FAILED_NO_EFFECT, INTERRUPTED_NO_EFFECT);

    /** The states that are final: no transition leaves them. */
    private static final Set<RequestExecutionState> TERMINAL = EnumSet.of(
            COMPLETED, INDETERMINATE, RECOVERY_REFUSED);

    /**
     * Whether a request in this state may be executed again.
     *
     * @return true only for the states that prove no effect happened
     */
    public boolean reExecutable() {
        return RE_EXECUTABLE.contains(this);
    }

    /**
     * Whether this state is final.
     *
     * @return true when no transition leaves this state
     */
    public boolean terminal() {
        return TERMINAL.contains(this);
    }

    /**
     * Whether a delivery that finds this state may be answered with the stored outcome.
     *
     * @return true for {@link #COMPLETED}
     */
    public boolean replayable() {
        return this == COMPLETED;
    }

    /**
     * Whether a request in this state is still unfinished work.
     *
     * @return true when the state is neither terminal nor completed
     */
    public boolean unfinished() {
        return !terminal();
    }
}
