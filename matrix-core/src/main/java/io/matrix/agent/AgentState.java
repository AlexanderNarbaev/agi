package io.matrix.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of the MPDT agent state at a single point in time.
 *
 * <p>Each state captures:
 * <ul>
 *   <li>{@code observation} — raw sensor bits from the environment</li>
 *   <li>{@code thought} — boolean vector produced by the brain (HierarchicalBrain)</li>
 *   <li>{@code action} — the action chosen in this tick</li>
 *   <li>{@code driverLevels} — snapshot of driver motivation levels</li>
 *   <li>{@code timestamp} — monotonic tick counter</li>
 * </ul>
 *
 * <p>Ref: L1_MPDT_neuron.md §4 (Observe → Think → Act cycle)
 */
public final class AgentState {

    private final long observation;
    private final boolean[] thought;
    private final AgentAction action;
    private final double[] driverLevels;
    private final long tick;
    private final long timestampMs;

    public AgentState(long observation, boolean[] thought, AgentAction action,
                      double[] driverLevels, long tick) {
        this.observation = observation;
        this.thought = thought == null ? new boolean[0] : thought.clone();
        this.action = action;
        this.driverLevels = driverLevels == null ? new double[0] : driverLevels.clone();
        this.tick = tick;
        this.timestampMs = System.currentTimeMillis();
    }

    // ── Accessors ──

    /** Raw sensor bits from environment observation. */
    public long observation() { return observation; }

    /** Boolean thought vector produced by the brain. Defensive copy. */
    public boolean[] thought() { return thought.clone(); }

    /** The action selected for this tick. */
    public AgentAction action() { return action; }

    /** Snapshot of driver motivation levels. Defensive copy. */
    public double[] driverLevels() { return driverLevels.clone(); }

    /** Monotonic tick counter. */
    public long tick() { return tick; }

    /** Wall-clock timestamp in milliseconds. */
    public long timestampMs() { return timestampMs; }

    // ── Derived ──

    /** Number of true bits in the thought vector (activation density). */
    public int thoughtActivation() {
        int count = 0;
        for (boolean b : thought) {
            if (b) count++;
        }
        return count;
    }

    /** Returns the action type, or null if no action. */
    public AgentAction.ActionType actionType() {
        return action == null ? null : action.type();
    }

    /**
     * Returns true if this state's action is the same type as the other state's action.
     */
    public boolean sameActionType(AgentState other) {
        if (other == null) return false;
        return actionType() == other.actionType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentState other)) return false;
        return observation == other.observation
                && tick == other.tick
                && Objects.equals(action, other.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(observation, tick, action);
    }

    @Override
    public String toString() {
        return "AgentState{tick=" + tick
                + ", obs=0x" + Long.toHexString(observation)
                + ", thought=" + thoughtActivation() + "/" + thought.length
                + ", action=" + (action != null ? action.type() : "null")
                + "}";
    }

    // ── History management ──

    /**
     * Builds an immutable history list from a mutable list of states.
     */
    public static List<AgentState> immutableHistory(List<AgentState> mutable) {
        return Collections.unmodifiableList(new ArrayList<>(mutable));
    }
}
