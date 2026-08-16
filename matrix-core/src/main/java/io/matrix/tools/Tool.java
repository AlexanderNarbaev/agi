package io.matrix.tools;

import java.util.Map;

/**
 * Tool contract: every tool has a unique name, description, and executes
 * with a map of arguments, returning a string result.
 *
 * <p>Per CONSTITUTION VII.1: tools must be sandboxed, deterministic for
 * the same input, and must not call LLM-based services.
 */
public interface Tool {

    /** Unique tool name (used as key in registry). */
    String name();

    /** Human-readable description for API listings. */
    String description();

    /**
     * Execute the tool with the given arguments.
     *
     * @param args tool-specific arguments (query, url, code, etc.)
     * @return execution result as a string (max 64KB)
     */
    String execute(Map<String, Object> args);
}
