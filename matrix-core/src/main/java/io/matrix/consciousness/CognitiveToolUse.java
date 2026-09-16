package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * W263 — Cognitive Tool Use (function calling).
 *
 * <p>Inspired by LLM function calling (OpenAI, Anthropic). Cognitive
 * system can invoke external "tools" and use results.
 *
 * <p>Tool registry + invoke.
 *
 * <p>CONSTITUTION VI compliance: tool-augmented cognitive processing,
 * not phenomenal consciousness claim.
 */
public final class CognitiveToolUse {

    public CognitiveToolUse() {}

    /** A tool definition. */
    public record Tool(
        String name,
        String description,
        java.util.function.Function<Map<String, Object>, String> executor
    ) {}

    /** Tool invocation result. */
    public record ToolResult(
        String toolName,
        Map<String, Object> parameters,
        String output,
        boolean success
    ) {}

    private final Map<String, Tool> tools = new HashMap<>();

    /**
     * Register a tool.
     */
    public void register(Tool tool) {
        if (tool != null && tool.name() != null) {
            tools.put(tool.name(), tool);
        }
    }

    /**
     * Invoke a tool by name.
     */
    public ToolResult invoke(String toolName, Map<String, Object> parameters) {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            return new ToolResult(toolName, parameters, "Tool not found: " + toolName, false);
        }
        try {
            String output = tool.executor().apply(parameters != null ? parameters : new HashMap<>());
            return new ToolResult(toolName, parameters, output, true);
        } catch (Exception e) {
            return new ToolResult(toolName, parameters, "Error: " + e.getMessage(), false);
        }
    }

    /**
     * List all registered tools.
     */
    public List<String> listTools() {
        return new ArrayList<>(tools.keySet());
    }

    /**
     * Number of registered tools.
     */
    public int toolCount() {
        return tools.size();
    }
}
