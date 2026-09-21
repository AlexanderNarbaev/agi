package io.matrix.actions;

import java.util.List;
import java.util.Map;

/**
 * Action Registry (DESIGN-13): typed action registry with ioSchema contracts.
 *
 * <p>Each action has:
 * <ul>
 *   <li>name: unique identifier</li>
 *   <li>ioSchema: input/output type schema (Hoare contracts)</li>
 *   <li>preconditions: what must be true before execution</li>
 *   <li>postconditions: what is guaranteed after execution</li>
 *   <li>executor: the actual implementation</li>
 * </ul>
 *
 * <p>Per DESIGN-13: actions are the executable units of the system.
 * Every action is typed, contracted, and registered in the central registry.
 */
public final class ActionRegistry {

    private final Map<String, Action> actions = new java.util.concurrent.ConcurrentHashMap<>();

    /** Register an action. */
    public void register(Action action) {
        actions.put(action.name(), action);
    }

    /** Get action by name. */
    public Action get(String name) {
        return actions.get(name);
    }

    /** List all registered actions. */
    public List<Action> list() {
        return List.copyOf(actions.values());
    }

    /** Check if action exists. */
    public boolean has(String name) {
        return actions.containsKey(name);
    }

    /** Unregister an action. */
    public void unregister(String name) {
        actions.remove(name);
    }

    /** Action contract. */
    public record Action(
            String name,
            IoSchema ioSchema,
            List<String> preconditions,
            List<String> postconditions,
            ActionExecutor executor) {

        /** Execute the action with input. */
        public ActionResult execute(Map<String, Object> input) {
            // Check preconditions
            for (String pre : preconditions) {
                if (!evaluatePrecondition(pre, input)) {
                    return ActionResult.preconditionFailed(name, pre);
                }
            }
            try {
                Object output = executor.execute(input);
                // Check postconditions
                for (String post : postconditions) {
                    if (!evaluatePostcondition(post, output)) {
                        return ActionResult.postconditionFailed(name, post);
                    }
                }
                return ActionResult.success(name, output);
            } catch (Exception e) {
                return ActionResult.failure(name, e.getMessage());
            }
        }

        private boolean evaluatePrecondition(String pre, Map<String, Object> input) {
            // Simple: check if input key exists and is non-null
            return input.containsKey(pre) && input.get(pre) != null;
        }

        private boolean evaluatePostcondition(String post, Object output) {
            // Simple: check output is non-null
            return output != null;
        }
    }

    /** Input/output type schema. */
    public record IoSchema(
            Map<String, Class<?>> inputTypes,
            Class<?> outputType) {}

    /** Action executor functional interface. */
    @FunctionalInterface
    public interface ActionExecutor {
        Object execute(Map<String, Object> input);
    }

    /** Action result. */
    public record ActionResult(
            String actionName,
            boolean success,
            Object output,
            String error) {
        public static ActionResult success(String name, Object output) {
            return new ActionResult(name, true, output, null);
        }
        public static ActionResult failure(String name, String error) {
            return new ActionResult(name, false, null, error);
        }
        public static ActionResult preconditionFailed(String name, String pre) {
            return new ActionResult(name, false, null, "precondition failed: " + pre);
        }
        public static ActionResult postconditionFailed(String name, String post) {
            return new ActionResult(name, false, null, "postcondition failed: " + post);
        }
    }
}
