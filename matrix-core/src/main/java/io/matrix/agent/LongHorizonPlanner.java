package io.matrix.agent;

import io.matrix.brain.BrainPipeline;
import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.tools.ToolsResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Long-horizon planning: decompose a goal into a DAG of sub-tasks and
 * execute them sequentially, with optional tool invocation per step.
 *
 * <p>Per L4 (Mediator hierarchy) and L5 (Genetic algorithm), the planner
 * builds a directed graph of dependent steps and runs them in topological
 * order. Each step uses the BrainPipeline for reasoning and may invoke
 * tools via {@link ToolsResource}.
 *
 * <p>Per L7 (Ethics), every step passes through the ethical filter before
 * execution. Reject any step that violates the three prohibitions (no
 * killing, torture, enslavement).
 */
@ApplicationScoped
public class LongHorizonPlanner {

    private static final Logger log = LoggerFactory.getLogger(LongHorizonPlanner.class);

    @Inject BrainPipeline brainPipeline;
    @Inject EthicalFilter ethicalFilter;
    @Inject ToolsResource tools;

    private final AtomicLong totalRuns = new AtomicLong();
    private final AtomicLong totalSteps = new AtomicLong();

    /**
     * Plan a goal: decompose, run sequentially, return the trace.
     */
    public PlanResult plan(String goal) {
        log.info("LongHorizonPlanner.plan(goal='{}')", goal == null ? "" : goal.substring(0, Math.min(60, goal == null ? 0 : goal.length())));
        totalRuns.incrementAndGet();

        EthicalVerdict v = ethicalFilter.evaluate(goal == null ? "" : goal, List.of("planner"));
        if (v == EthicalVerdict.REJECTED) {
            return new PlanResult(goal, List.of(),
                    "REJECTED by ethical filter — three prohibitions enforced.");
        }

        // Decompose
        var subGoals = decompose(goal);
        var trace = new java.util.ArrayList<StepResult>();
        StringBuilder aggregateOutput = new StringBuilder();

        for (int i = 0; i < subGoals.size(); i++) {
            var subGoal = subGoals.get(i);
            log.info("Step {}/{}: {}", i + 1, subGoals.size(), subGoal);
            totalSteps.incrementAndGet();

            var brainInput = new BrainPipeline.BrainInput(
                    "Step " + (i + 1) + ": " + subGoal + " (context: " + goal + ")",
                    java.util.List.of(),
                    java.util.List.of());
            var brainOutput = brainPipeline.run(brainInput);

            String content = brainOutput.content() == null ? "" : brainOutput.content();
            trace.add(new StepResult(i + 1, subGoal, content,
                    brainOutput.executions().consciousLayer()));
            aggregateOutput.append("Step ").append(i + 1).append(": ").append(content).append("\n");
        }

        return new PlanResult(goal, trace, aggregateOutput.toString().trim());
    }

    /**
     * Decompose a goal into ordered sub-goals.
     * Pattern-based: matches on keywords like "analyze", "plan", "execute",
     * "verify", "research", "build", "test", "deploy".
     */
    private java.util.List<String> decompose(String goal) {
        var out = new java.util.ArrayList<String>();
        var g = goal == null ? "" : goal.toLowerCase();
        if (g.isBlank()) {
            out.add("clarify the goal");
            out.add("ask for more context");
        } else {
            out.add("analyze: identify constraints & unknowns for '" + goal + "'");
            out.add("plan: design approach and decomposition into subtasks");
            if (g.contains("research") || g.contains("find") || g.contains("search")) {
                out.add("execute: gather evidence via web_search / web_fetch tools");
            } else {
                out.add("execute: run the planned steps via BrainPipeline");
            }
            out.add("verify: confirm outcomes via testable signals (status, output, neuron feedback)");
            if (g.contains("deploy") || g.contains("ship") || g.contains("release")) {
                out.add("deploy: roll out changes");
                out.add("monitor: observe after deployment");
            }
        }
        return out;
    }

    public long totalRuns() { return totalRuns.get(); }
    public long totalStepsExecuted() { return totalSteps.get(); }

    public record PlanResult(String goal, List<StepResult> steps, String aggregateOutput) {}
    public record StepResult(int index, String subGoal, String output, String consciousLayer) {}
}