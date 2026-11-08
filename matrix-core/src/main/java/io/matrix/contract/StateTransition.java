package io.matrix.contract;

import java.util.List;
import java.util.Map;

/**
 * W51 — a change of the world state, with its cause.
 *
 * <p><b>A transition is a fact about the world, not about the system's belief.</b> It says
 * what changed, from which state to which state, and what caused it. The system's estimate of
 * the new state is a {@link WorldState} and carries the system's own confidence; the
 * transition itself is the event that happened.</p>
 *
 * <p><b>The cause is named.</b> {@link #cause()} says whether the transition was caused by an
 * action, by an observation, or by something outside the system. This is what makes a trace
 * reconstructable: the chain from the action to the state change is explicit, and a
 * transition with no cause is an observation of a change the system cannot explain.</p>
 *
 * <p><b>The change is a diff, not a copy.</b> {@link #changedProperties()} names what changed
 * and {@link #changedRelations()} names what was added or removed. A transition that carried
 * the whole new state would make the history unreadable and would hide which part of the
 * state the system actually learned.</p>
 *
 * @param id                the identity of the transition
 * @param environmentId     the domain whose state changed
 * @param fromStateId       the state before the change
 * @param toStateId         the state after the change
 * @param cause             what caused the change
 * @param actionId          the action that caused it, when the cause was an action
 * @param changedProperties the properties that changed
 * @param changedRelations  the relations that were added or removed
 * @param at                when the transition happened
 */
public record StateTransition(
        String id,
        String environmentId,
        String fromStateId,
        String toStateId,
        Cause cause,
        String actionId,
        Map<String, Object> changedProperties,
        List<String> changedRelations,
        String at) {

    /** What caused a state transition. */
    public enum Cause {
        /** The system acted. */
        ACTION,
        /** The system observed a change it did not cause. */
        OBSERVATION,
        /** The change came from outside the system. */
        EXTERNAL,
        /** The cause is not known. */
        UNKNOWN
    }

    public StateTransition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("transition id must not be blank");
        }
        if (environmentId == null || environmentId.isBlank()) {
            throw new IllegalArgumentException("transition environment id must not be blank");
        }
        if (cause == null) {
            throw new IllegalArgumentException("transition cause must not be null");
        }
        changedProperties = changedProperties == null ? Map.of() : Map.copyOf(changedProperties);
        changedRelations = changedRelations == null ? List.of() : List.copyOf(changedRelations);
    }

    /** True when the system caused the change itself. */
    public boolean agentCaused() {
        return cause == Cause.ACTION && actionId != null;
    }

    @Override
    public String toString() {
        return "StateTransition[" + id + " " + fromStateId + " -> " + toStateId + " " + cause + "]";
    }
}
