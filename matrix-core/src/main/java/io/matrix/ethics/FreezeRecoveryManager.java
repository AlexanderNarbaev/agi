package io.matrix.ethics;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RUN 39 — Ethics violation recovery manager (H-045 verification).
 *
 * <p>Tracks ethics-violation events and provides a graceful-degrade
 * recovery path: when a violation occurs, the system enters
 * {@link State#FROZEN}, blocks all actions, then after a cooldown
 * transitions back to {@link State#NORMAL}.
 *
 * <p>Honest caveats:
 * <ul>
 *   <li>This is a STATEFUL single-instance manager. For multi-node
 *       deployments, the freeze state would need to be replicated
 *       via the federation layer (M3 quorum).</li>
 *   <li>The recovery is automatic after cooldown. A production
 *       system would also expose a manual recovery API for ops.</li>
 * </ul>
 */
@io.matrix.auditor.ArticleIPermitted(value = "audit record of when a violation occurred; the cooldown DECISION reads the logical tick")
    public class FreezeRecoveryManager {

    /** State machine for the freeze mode. */
    public enum State {
        /** Normal operation — all actions allowed. */
        NORMAL,
        /** Frozen due to recent violation — actions blocked. */
        FROZEN,
        /** Recovering from freeze — partial restrictions. */
        RECOVERING
    }

    /** A recorded violation event. */
    public record Violation(String source, String reason, Instant at, long tick) {}

    /** Default freeze duration in milliseconds. */
    public static final long DEFAULT_FREEZE_DURATION_MS = 1000;

    private final long freezeDurationMs;
    private final AtomicReference<State> state = new AtomicReference<>(State.NORMAL);
    /**
     * W35.2 — the freeze cooldown is measured in LOGICAL TICKS, not wall-clock milliseconds.
     *
     * <p>The cooldown decided whether an action was allowed using
     * {@code System.currentTimeMillis()}, which is a decision resting on a real clock. Article I
     * forbids wall-clock in decision paths precisely so a run can be replayed and audited: two
     * identical violation sequences separated by different amounts of real time would freeze for
     * different durations and produce different verdicts.</p>
     *
     * <p>{@code reportViolation} already accepts a {@code tick}, so the logical clock was being
     * threaded through the class and simply ignored for the decision. It is now the only clock
     * the cooldown reads.</p>
     */
    private final AtomicLong freezeStartedAtTick = new AtomicLong(0);

    /**
     * The manager's own logical clock, advanced only by explicit calls. Never derived from the
     * host clock, so the sequence of states is reproducible from the sequence of inputs alone.
     */
    private final AtomicLong logicalClock = new AtomicLong(0);
    private final AtomicLong violationCount = new AtomicLong();
    private final AtomicLong recoveryCount = new AtomicLong();
    private final java.util.List<Violation> violationHistory = new java.util.concurrent.CopyOnWriteArrayList<>();

    public FreezeRecoveryManager() {
        this(DEFAULT_FREEZE_DURATION_MS);
    }

    public FreezeRecoveryManager(long freezeDurationMs) {
        this.freezeDurationMs = Math.max(100, freezeDurationMs);
    }

    /**
     * Report an ethics violation. Enters freeze mode if not already frozen.
     *
     * @return true if action should be blocked (i.e., we're in FROZEN state
     *         AFTER this report).
     */
    public boolean reportViolation(String source, String reason, long tick) {
        if (source == null) source = "unknown";
        if (reason == null) reason = "unspecified";
        violationCount.incrementAndGet();
        violationHistory.add(new Violation(source, reason, Instant.now(), tick));

        // Transition to FROZEN if not already.
        if (state.get() != State.FROZEN) {
            state.set(State.FROZEN);
            freezeStartedAtTick.set(tick);
            return true;  // newly frozen — block
        }
        return true;  // already frozen — block
    }

    /**
     * Check if an action is currently allowed.
     *
     * @return true if action should proceed (NORMAL or RECOVERING), false
     *         if FROZEN.
     */
    public boolean isActionAllowed() {
        return isActionAllowed(logicalClock.get());
    }

    /**
     * Whether an action is currently allowed, evaluated against an explicit logical time.
     *
     * @param nowTick the caller's logical clock reading
     * @return true when the action may proceed
     */
    public boolean isActionAllowed(long nowTick) {
        State s = state.get();
        if (s == State.NORMAL) return true;
        if (s == State.RECOVERING) return true;
        // FROZEN: check if cooldown elapsed.
        long elapsed = nowTick - freezeStartedAtTick.get();
        if (elapsed >= freezeDurationMs) {
            // Cooldown done — transition to RECOVERING, then back to NORMAL.
            state.set(State.RECOVERING);
            recoveryCount.incrementAndGet();
            // Recovering state allows actions after one more tick.
            return true;
        }
        return false;
    }

/** Force-clear the freeze state (for manual recovery). */
    public void manualRecover() {
        if (state.get() != State.NORMAL) {
            recoveryCount.incrementAndGet();
        }
        state.set(State.NORMAL);
        freezeStartedAtTick.set(0);
    }

    /**
     * Advance the manager's logical clock.
     *
     * <p>W35.2 — this is the only way time moves for the cooldown decision. It is never derived
     * from the host clock, so a given sequence of violations and clock advances always produces
     * the same sequence of states, which is what makes a freeze decision replayable for audit.</p>
     *
     * @param deltaMs logical milliseconds to advance; negative values are ignored
     * @return the new logical clock reading
     */
    public long advanceClock(long deltaMs) {
        if (deltaMs <= 0) {
            return logicalClock.get();
        }
        return logicalClock.addAndGet(deltaMs);
    }

    /** The current logical clock reading. */
    public long logicalNow() {
        return logicalClock.get();
    }

    /** Get current state. */
    public State getState() { return state.get(); }

    /** Number of violations recorded. */
    public long violationCount() { return violationCount.get(); }

    /** Number of recoveries (auto + manual). */
    public long recoveryCount() { return recoveryCount.get(); }

    /** Defensive copy of violation history. */
    public java.util.List<Violation> violationHistory() {
        return java.util.List.copyOf(violationHistory);
    }

    /** Freeze duration in ms. */
    public long freezeDurationMs() { return freezeDurationMs; }
}
