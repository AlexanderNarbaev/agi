package io.matrix.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An action taken by the MPDT agent in the Observe → Think → Act cycle.
 *
 * <p>Each action has a type, optional parameters, and produces a result
 * after execution. Actions are immutable once created.
 *
 * <p>Ref: L1_MPDT_neuron.md §4 (Act phase)
 */
public final class AgentAction {

    /**
     * Supported action types for the MPDT agent loop.
     */
    public enum ActionType {
        MOVE("Move to adjacent cell"),
        SPEAK("Communicate with another agent"),
        THINK("Internal deliberation"),
        WAIT("Wait for environment change"),
        OBSERVE("Sense the environment"),
        MINE("Mine a resource block"),
        CRAFT("Craft an item"),
        EAT("Consume food for energy"),
        TOOL_UP("Upgrade or equip a tool"),
        EXPLORE("Random exploration move");

        private final String description;

        ActionType(String description) {
            this.description = description;
        }

        public String description() { return description; }
    }

    /**
     * Immutable result of executing an action.
     */
    public record ActionResult(boolean success, String output, long durationMs) {
        public static ActionResult success(String output, long durationMs) {
            return new ActionResult(true, output, durationMs);
        }

        public static ActionResult failure(String output, long durationMs) {
            return new ActionResult(false, output, durationMs);
        }

        public static ActionResult empty() {
            return new ActionResult(true, "", 0);
        }
    }

    private final ActionType type;
    private final Map<String, Object> parameters;
    private final ActionResult result;

    public AgentAction(ActionType type) {
        this(type, Collections.emptyMap(), null);
    }

    public AgentAction(ActionType type, Map<String, Object> parameters) {
        this(type, Collections.unmodifiableMap(new LinkedHashMap<>(parameters)), null);
    }

    public AgentAction(ActionType type, Map<String, Object> parameters, ActionResult result) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.parameters = parameters == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        this.result = result;
    }

    /**
     * Returns a new action with the given result attached (immutable copy).
     */
    public AgentAction withResult(ActionResult result) {
        return new AgentAction(this.type, this.parameters, result);
    }

    public ActionType type() { return type; }

    public Map<String, Object> parameters() { return parameters; }

    public ActionResult result() { return result; }

    public boolean hasResult() { return result != null; }

    /**
     * Returns the parameter value for the given key, or the default.
     */
    public Object paramOrDefault(String key, Object defaultValue) {
        return parameters.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentAction other)) return false;
        return type == other.type && parameters.equals(other.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, parameters);
    }

    @Override
    public String toString() {
        return "AgentAction{type=" + type
                + ", params=" + parameters
                + (result != null ? ", result=" + result : "")
                + "}";
    }
}
