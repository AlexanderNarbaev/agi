package io.matrix.actions;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Hoare-style plan runner over a deterministic state map (DESIGN-13 §2).
 *
 * <p>Each step is a triple {@code P{effect}Q}: the step executes only when
 * precondition {@code P} holds on the current state; afterwards postcondition
 * {@code Q} must hold. A plan-level invariant is checked after every step.
 * Violations fail fast with a deterministic reason — no partial application
 * escapes the runner (effects are applied to a working copy that is committed
 * only on success).
 *
 * <p>Deterministic: pure predicates/operators, no randomness, no wall-clock.
 */
public final class PlanRunner {

    private PlanRunner() {}

    /** One planned action: {@code P{effect}Q}. */
    public record Step(String name,
                       Predicate<Map<String, Object>> precondition,
                       Predicate<Map<String, Object>> postcondition,
                       UnaryOperator<Map<String, Object>> effect) {

        public Step {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("step name must not be blank");
            }
            java.util.Objects.requireNonNull(precondition, "precondition");
            java.util.Objects.requireNonNull(postcondition, "postcondition");
            java.util.Objects.requireNonNull(effect, "effect");
        }
    }

    /**
     * Executes the plan atomically.
     *
     * @param steps    ordered plan
     * @param state    initial state (not mutated)
     * @param invariant plan-level invariant, checked after each step
     * @return the final committed state
     * @throws IllegalStateException with reasons {@code precondition_violated},
     *         {@code postcondition_violated}, or {@code invariant_violated}
     */
    public static Map<String, Object> run(List<Step> steps,
                                          Map<String, Object> state,
                                          Predicate<Map<String, Object>> invariant) {
        java.util.Objects.requireNonNull(steps, "steps");
        Map<String, Object> current = new java.util.HashMap<>(state);
        for (Step step : steps) {
            if (!step.precondition().test(current)) {
                throw new IllegalStateException(
                        "precondition_violated at step " + step.name());
            }
            current = step.effect().apply(current);
            if (!step.postcondition().test(current)) {
                throw new IllegalStateException(
                        "postcondition_violated at step " + step.name());
            }
            if (invariant != null && !invariant.test(current)) {
                throw new IllegalStateException(
                        "invariant_violated at step " + step.name());
            }
        }
        return java.util.Collections.unmodifiableMap(current);
    }
}
