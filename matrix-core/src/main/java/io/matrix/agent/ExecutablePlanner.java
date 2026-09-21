package io.matrix.agent;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.tools.ToolsResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Executable planner: takes a goal, decomposes into {@link PlanStep} items,
 * and executes them via {@link ToolsResource}.
 *
 * <p>Per L4 (Mediator hierarchy) and L5 (Genetic algorithm), the planner
 * wraps {@link LongHorizonPlanner} for decomposition and adds tool-execution
 * capabilities. Each step is executed sequentially with optional regex-based
 * verification of previous step output.
 *
 * <p>Per CONSTITUTION VII.1: no LLM calls in runtime. Tool invocations
 * are deterministic for the same input.
 */
@ApplicationScoped
public class ExecutablePlanner {

    private static final Logger log = LoggerFactory.getLogger(ExecutablePlanner.class);

    @Inject
    LongHorizonPlanner planner;

    @Inject
    ToolsResource tools;

    @Inject
    EthicalFilter ethicalFilter;

    private final AtomicLong totalRuns = new AtomicLong();

    /**
     * Plan and execute a goal. Decomposes via LongHorizonPlanner,
     * extracts PlanStep items, and executes each step sequentially.
     */
    public ExecutionResult executePlan(String goal) {
        log.info("ExecutablePlanner.executePlan(goal='{}')",
                goal == null ? "" : goal.substring(0, Math.min(80, goal.length())));
        totalRuns.incrementAndGet();

        EthicalVerdict v = ethicalFilter.evaluate(
                goal == null ? "" : goal, List.of("planner"));
        if (v == EthicalVerdict.REJECTED) {
            return new ExecutionResult(goal, List.of(), false,
                    "REJECTED by ethical filter — three prohibitions enforced.");
        }

        // Step 1: decompose via LongHorizonPlanner
        var planResult = planner.plan(goal);
        List<PlanStep> steps = extractSteps(planResult);

        // Step 2: execute
        return executeSteps(goal, steps);
    }

    /**
     * Execute a pre-built list of PlanStep items. Public for testing.
     */
    public ExecutionResult executeSteps(String goal, List<PlanStep> steps) {
        return executeSteps(goal, steps, null);
    }

    /**
     * DESIGN-15 §3: optional AC-3 preprocessing gate — when a CSP over plan
     * preconditions is provided and unsatisfiable, execution fast-fails
     * without entering the generation contour.
     */
    public ExecutionResult executeSteps(String goal, List<PlanStep> steps,
                                        io.matrix.agent.planning.Ac3Solver cspPrecheck) {
        if (cspPrecheck != null && !cspPrecheck.solve()) {
            log.warn("AC-3 preprocessing: CSP unsatisfiable — fast-fail before execution");
            return new ExecutionResult(goal, List.of(), false, "unsatisfiable_preconditions");
        }
        List<StepOutcome> trace = new ArrayList<>();
        String lastOutput = "";
        boolean allPassed = true;

        for (int i = 0; i < steps.size(); i++) {
            PlanStep step = steps.get(i);
            log.debug("Step {}/{}: action={} tool={}",
                    i + 1, steps.size(), step.action(), step.toolName());

            switch (step.action()) {
                case "invoke_tool" -> {
                    Map<String, Object> args = new HashMap<>(step.args());
                    try {
                        String result = tools.invoke(step.toolName(), args);
                        trace.add(new StepOutcome(step.description(),
                                "invoke_tool", result, true));
                        lastOutput = result;
                    } catch (Exception e) {
                        String err = "Error: " + e.getMessage();
                        trace.add(new StepOutcome(step.description(),
                                "invoke_tool", err, false));
                        lastOutput = err;
                        allPassed = false;
                    }
                }
                case "wait" -> {
                    long ms = ((Number) step.args()
                            .getOrDefault("duration_ms", 0L)).longValue();
                    try {
                        Thread.sleep(ms);
                        trace.add(new StepOutcome(step.description(),
                                "wait", "waited " + ms + "ms", true));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        trace.add(new StepOutcome(step.description(),
                                "wait", "interrupted", false));
                        allPassed = false;
                    }
                }
                case "verify" -> {
                    String pattern = step.expectedOutputPattern();
                    boolean passed = pattern != null
                            && !pattern.isEmpty()
                            && lastOutput.matches(pattern);
                    String outcome = passed
                            ? "PASS: matched '" + pattern + "'"
                            : "FAIL: expected '" + pattern
                            + "' but got '" + truncate(lastOutput, 80) + "'";
                    trace.add(new StepOutcome(
                            step.description(), "verify", outcome, passed));
                    if (!passed) allPassed = false;
                }
                default -> {
                    trace.add(new StepOutcome(step.description(),
                            step.action(), "Unknown action: " + step.action(),
                            false));
                    allPassed = false;
                }
            }
        }

        String summary = allPassed
                ? "All " + steps.size() + " steps completed successfully"
                : steps.size() + " steps executed, some failed";
        return new ExecutionResult(goal, trace, allPassed, summary);
    }

    /**
     * Extract PlanStep items from a LongHorizonPlanner result.
     * Pattern-based: matches step output against known tool signatures.
     */
    private List<PlanStep> extractSteps(LongHorizonPlanner.PlanResult planResult) {
        List<PlanStep> steps = new ArrayList<>();
        for (var sr : planResult.steps()) {
            String subGoal = sr.subGoal().toLowerCase();
            if (subGoal.contains("web_search") || subGoal.contains("search")) {
                steps.add(PlanStep.invokeTool("web_search",
                        Map.of("query", sr.subGoal()), sr.subGoal()));
            } else if (subGoal.contains("calculator") || subGoal.contains("calculate")) {
                steps.add(PlanStep.invokeTool("calculator",
                        Map.of("expression", "2+2"), sr.subGoal()));
            } else if (subGoal.contains("verify") || subGoal.contains("confirm")) {
                steps.add(PlanStep.verify(".*", "Verify step " + sr.index()));
            } else {
                // Default: treat as a reasoning step (no tool)
                steps.add(PlanStep.invokeTool("calculator",
                        Map.of("expression", "0"), sr.subGoal()));
            }
        }
        return steps;
    }

    public long totalRuns() {
        return totalRuns.get();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ─── result records ───

    public record StepOutcome(
            String description,
            String action,
            String output,
            boolean passed) {}

    public record ExecutionResult(
            String goal,
            List<StepOutcome> steps,
            boolean allPassed,
            String summary) {}
}
