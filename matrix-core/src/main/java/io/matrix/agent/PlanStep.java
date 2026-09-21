package io.matrix.agent;

import java.util.Map;

/**
 * A single executable step in a long-horizon plan.
 *
 * <p>Each step has an action type that determines how it is executed:
 * <ul>
 *   <li>{@code invoke_tool} — calls a named tool via {@link io.matrix.tools.ToolsResource}</li>
 *   <li>{@code wait} — pauses for a specified duration</li>
 *   <li>{@code verify} — checks previous step output against an expected pattern</li>
 * </ul>
 *
 * <p>Per CONSTITUTION VII.1: no LLM calls in runtime. Tool invocations
 * are deterministic for the same input.
 *
 * @param action one of "invoke_tool", "wait", "verify"
 * @param toolName the tool to invoke (null for non-tool actions)
 * @param args tool-specific arguments
 * @param expectedOutputPattern regex pattern for verify steps (null if not applicable)
 * @param description human-readable description of this step
 */
public record PlanStep(
        String action,
        String toolName,
        Map<String, Object> args,
        String expectedOutputPattern,
        String description) {

    /** Create an invoke_tool step. */
    public static PlanStep invokeTool(String toolName, Map<String, Object> args, String description) {
        return new PlanStep("invoke_tool", toolName, args, null, description);
    }

    /** Create a verify step with an expected regex pattern. */
    public static PlanStep verify(String expectedPattern, String description) {
        return new PlanStep("verify", null, Map.of(), expectedPattern, description);
    }

    /** Create a wait step with duration in milliseconds. */
    public static PlanStep waitMs(long millis, String description) {
        return new PlanStep("wait", null, Map.of("duration_ms", millis), null, description);
    }
}
