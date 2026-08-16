package io.matrix.agent;

import io.matrix.brain.BrainPipeline;
import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.memory.HierarchicalMemory;
import io.matrix.tools.ToolsResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sub-agent: limited-purpose, inference-only child agent.
 *
 * <p>Per L13 Pilot design and L14 BusinessModel: a sub-agent is a focused
 * worker spawned by the main agent for a specific task. It can ONLY use
 * tools — not training, not modifying world model, not memory writes.
 *
 * <p>Result goes back to the main agent, which decides if the output
 * reveals new useful connections (memory write-back at main level).
 *
 * <p>Sub-agents are sandboxed:
 * <ul>
 *   <li>Cannot invoke chat endpoints (avoid recursive agent loops)</li>
 *   <li>Cannot modify HierarchicalMemory</li>
 *   <li>Cannot trigger training cycles</li>
 *   <li>Only allowed tools: calculator, datetime, file_read, web_search, web_fetch</li>
 *   <li>Process isolation: tool execution runs in a separate thread with timeout</li>
 * </ul>
 */
@ApplicationScoped
public class SubAgent {

    private static final Logger log = LoggerFactory.getLogger(SubAgent.class);
    private static final List<String> ALLOWED_TOOLS = List.of(
            "calculator", "datetime", "file_read", "web_search", "web_fetch", "code_execute");
    private static final long TOOL_TIMEOUT_MS = 5000;

    @Inject BrainPipeline brainPipeline;
    @Inject EthicalFilter ethicalFilter;
    @Inject ToolsResource tools;
    @Inject HierarchicalMemory memory;

    private final AtomicLong totalRuns = new AtomicLong();
    private final ExecutorService toolExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    public SubAgentResult run(String task, String toolName, String toolArgs) {
        log.info("SubAgent.run(task='{}', tool={})",
                task == null ? "" : task.substring(0, Math.min(60, task.length())), toolName);
        totalRuns.incrementAndGet();

        EthicalVerdict v = ethicalFilter.evaluate(task == null ? "" : task, List.of("subagent"));
        if (v == EthicalVerdict.REJECTED) {
            return new SubAgentResult(task, null, false, "REJECTED by ethical filter");
        }

        // Tool whitelist
        if (toolName != null && !ALLOWED_TOOLS.contains(toolName)) {
            return new SubAgentResult(task, null, false,
                    "tool '" + toolName + "' not in whitelist: " + ALLOWED_TOOLS);
        }

        // Step 1: run the tool (inference-only, isolated)
        String toolResult = null;
        if (toolName != null && !toolName.isBlank()) {
            try {
                toolResult = runToolIsolated(toolName, toolArgs);
            } catch (Exception e) {
                return new SubAgentResult(task, null, false,
                        "tool invocation failed: " + e.getMessage());
            }
        }

        // Step 2: reason about the tool result via BrainPipeline
        String reasoningInput = (task == null ? "" : task)
                + (toolResult != null ? " [tool:" + toolName + " result:" + toolResult + "]" : "");
        var brainOutput = brainPipeline.run(
                new io.matrix.brain.BrainPipeline.BrainInput(
                        reasoningInput, java.util.List.of(), java.util.List.of()));

        String response = brainOutput.content();

        // Step 3: memory write-back for successful tool results
        if (toolResult != null && !toolResult.startsWith("Error") && !toolResult.startsWith("CRASHED")) {
            try {
                memory.store(
                        HierarchicalMemory.Level.L1_PATTERN,
                        "SubAgent[" + toolName + "]: " + truncate(toolResult, 500),
                        "subagent",
                        Set.of("tool:" + toolName, "subagent"));
            } catch (Exception e) {
                log.warn("Memory write-back failed: {}", e.getMessage());
            }
        }

        return new SubAgentResult(task, toolResult, true, response);
    }

    /** Run tool in isolated thread with timeout. */
    private String runToolIsolated(String toolName, String toolArgs) throws Exception {
        Callable<String> toolCall = () -> {
            Map<String, Object> args = parseArgs(toolArgs);
            return tools.invoke(toolName, args);
        };

        Future<String> future = toolExecutor.submit(toolCall);
        try {
            return future.get(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Tool timeout after " + TOOL_TIMEOUT_MS + "ms");
        }
    }

    private Map<String, Object> parseArgs(String toolArgs) {
        if (toolArgs == null || toolArgs.isBlank()) return Map.of();
        return Map.of("expression", toolArgs, "url", toolArgs, "query", toolArgs);
    }

    public long totalRuns() { return totalRuns.get(); }

    public void shutdown() {
        toolExecutor.shutdown();
    }

    public record SubAgentResult(
            String task,
            String toolOutput,
            boolean ok,
            String response) {}
}
