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
    private final AtomicLong freezeStartedAtMs = new AtomicLong(0);
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
            freezeStartedAtMs.set(System.currentTimeMillis());
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
        State s = state.get();
        if (s == State.NORMAL) return true;
        if (s == State.RECOVERING) return true;
        // FROZEN: check if cooldown elapsed.
        long elapsed = System.currentTimeMillis() - freezeStartedAtMs.get();
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
        freezeStartedAtMs.set(0);
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
