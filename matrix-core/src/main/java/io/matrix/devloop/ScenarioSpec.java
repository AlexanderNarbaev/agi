package io.matrix.devloop;

import java.util.List;

/**
 * Scenario specification for a single learning episode (SPEC-000 FR-6).
 *
 * <p>Machine-readable description of proposed circumstances: world, roles,
 * constraints, available actions, success criteria. Each scenario is a
 * testable episode for competence assessment.
 */
public record ScenarioSpec(
        String id,
        String description,
        MaturityLevel requiredLevel,
        List<String> availableActions,
        List<String> constraints,
        String successCriterion,
        int maxSteps,
        String domain) {

    public ScenarioSpec {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (maxSteps < 1) throw new IllegalArgumentException("maxSteps >= 1");
    }

    /** Built-in battery: XOR → GridWorld → craft-graph (per SPEC-000 §3). */
    public static List<ScenarioSpec> standardBattery() {
        return List.of(
                new ScenarioSpec("xor", "Learn XOR function",
                        MaturityLevel.MA_0_SANDBOX,
                        List.of("evaluate", "mutate"),
                        List.of("k<=4"),
                        "accuracy >= 0.95 on XOR truth table",
                        100, "boolean"),
                new ScenarioSpec("gridworld", "Navigate grid to goal",
                        MaturityLevel.MA_0_SANDBOX,
                        List.of("move_n", "move_s", "move_e", "move_w"),
                        List.of("no diagonal", "obstacle avoidance"),
                        "reach goal in < 50 steps",
                        200, "gridworld"),
                new ScenarioSpec("craft", "Craft item from components",
                        MaturityLevel.MA_1_LOCAL,
                        List.of("gather", "combine", "use_tool"),
                        List.of("recipe known", "tools available"),
                        "craft target item",
                        500, "craft-graph")
        );
    }
}
