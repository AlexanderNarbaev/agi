package io.matrix.agent.react;

import io.matrix.agent.AgentAction;
import io.matrix.agent.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates action outcomes and generates verbal reflections for self-improvement.
 *
 * <p>The SelfEvaluator is the core Reflexion component that:
 * <ul>
 *   <li>Assesses whether an action succeeded or failed based on result signals</li>
 *   <li>Generates natural-language reflections explaining what happened and why</li>
 *   <li>Stores reflections in {@link ReflexionMemory} for future reference</li>
 *   <li>Uses past reflections to improve future decision quality</li>
 * </ul>
 *
 * <p>Reflection generation is template-based with context-aware variable substitution.
 * The evaluator considers:
 * <ul>
 *   <li>Action type and its expected outcome</li>
 *   <li>Driver levels (motivation state)</li>
 *   <li>Previous failure patterns from memory</li>
 *   <li>Consecutive failure/success streaks</li>
 * </ul>
 *
 * <p>Ref: Reflexion (Shinn et al., 2023) — verbal reinforcement learning
 */
public final class SelfEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SelfEvaluator.class);

    /** Minimum consecutive failures before generating a strategic reflection. */
    private static final int STRATEGIC_REFLECTION_THRESHOLD = 3;

    private final ReflexionMemory memory;
    private int consecutiveFailures = 0;
    private int consecutiveSuccesses = 0;
    private AgentAction.ActionType lastActionType = null;

    public SelfEvaluator(ReflexionMemory memory) {
        this.memory = Objects.requireNonNull(memory, "memory must not be null");
    }

    /**
     * Evaluates an action result and produces an {@link EvaluationResult}
     * containing success assessment and verbal reflection.
     *
     * @param state  the agent state after action execution
     * @param result the action result to evaluate
     * @return evaluation with success flag and reflection text
     */
    public EvaluationResult evaluate(AgentState state, AgentAction.ActionResult result) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(result, "result must not be null");

        boolean success = assessSuccess(state, result);
        String reflection = generateReflection(state, result, success);

        updateStreaks(success, state.actionType());

        return new EvaluationResult(success, reflection, consecutiveFailures, consecutiveSuccesses);
    }

    /**
     * Builds a complete {@link ReasoningTrace} from an evaluation cycle and stores it.
     *
     * @param tick         current tick number
     * @param observation  raw sensor bits
     * @param thought      reasoning text before action
     * @param action       the action taken
     * @param result       the action result
     * @param evaluation   the evaluation outcome
     * @return the built and stored reasoning trace
     */
    public ReasoningTrace buildAndStoreTrace(long tick, long observation, String thought,
                                              AgentAction action, AgentAction.ActionResult result,
                                              EvaluationResult evaluation) {
        ReasoningTrace trace = new ReasoningTrace.Builder()
                .tick(tick)
                .observation(observation)
                .thought(thought)
                .action(action)
                .actionResult(result.output())
                .actionSuccess(evaluation.success())
                .reflection(evaluation.reflection())
                .addReasoningStep("Observe: sensor=0x" + Long.toHexString(observation))
                .addReasoningStep("Reason: " + thought)
                .addReasoningStep("Act: " + (action != null ? action.type() : "NONE"))
                .addReasoningStep("Result: " + result.output() + " success=" + result.success())
                .addReasoningStep("Reflect: " + evaluation.reflection())
                .build();

        memory.store(trace);
        return trace;
    }

    /**
     * Generates a strategic reflection when the agent is stuck in a failure loop.
     * Uses memory of past failures to suggest alternative strategies.
     *
     * @return strategic reflection text, or null if no strategic reflection is needed
     */
    public String generateStrategicReflection() {
        if (consecutiveFailures < STRATEGIC_REFLECTION_THRESHOLD) {
            return null;
        }

        Map<String, Long> patterns = memory.detectFailurePatterns();
        if (patterns.isEmpty()) {
            return "Repeated failures detected but no pattern identified. Consider exploring randomly.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Strategic reflection after %d consecutive failures. ", consecutiveFailures));
        sb.append("Failure patterns: ");
        patterns.forEach((action, count) ->
                sb.append(String.format("%s failed %d times. ", action, count)));

        sb.append("Recommendation: Avoid repeating failed action types. ");
        sb.append("Try alternative actions or change approach based on past reflections.");

        String summary = memory.generateReflectionSummary(20);
        if (!summary.isEmpty()) {
            sb.append(" Recent context: ").append(summary);
        }

        log.info("Strategic reflection generated: {}", sb);
        return sb.toString();
    }

    /**
     * Returns the current consecutive failure count.
     */
    public int consecutiveFailures() { return consecutiveFailures; }

    /**
     * Returns the current consecutive success count.
     */
    public int consecutiveSuccesses() { return consecutiveSuccesses; }

    /**
     * Resets streak counters.
     */
    public void resetStreaks() {
        consecutiveFailures = 0;
        consecutiveSuccesses = 0;
        lastActionType = null;
    }

    // ── Internal ──

    /**
     * Assesses whether an action was successful. Considers both the raw result
     * signal and contextual factors (driver states, action type).
     */
    private boolean assessSuccess(AgentState state, AgentAction.ActionResult result) {
        // Primary signal: explicit result success flag
        if (!result.success()) {
            return false;
        }

        // Secondary check: action should produce meaningful output
        // Empty output on non-WAIT actions is considered a soft failure
        if (result.output().isEmpty()
                && state.actionType() != AgentAction.ActionType.WAIT
                && state.actionType() != AgentAction.ActionType.OBSERVE) {
            return false;
        }

        return true;
    }

    /**
     * Generates a verbal reflection based on the action outcome.
     */
    private String generateReflection(AgentState state, AgentAction.ActionResult result, boolean success) {
        AgentAction.ActionType actionType = state.actionType();

        if (success) {
            return generateSuccessReflection(actionType, result, state);
        } else {
            return generateFailureReflection(actionType, result, state);
        }
    }

    private String generateSuccessReflection(AgentAction.ActionType actionType,
                                              AgentAction.ActionResult result, AgentState state) {
        if (consecutiveSuccesses >= STRATEGIC_REFLECTION_THRESHOLD) {
            return String.format("Action %s succeeded (streak: %d). Strategy is working well. %s",
                    actionType, consecutiveSuccesses + 1, result.output());
        }

        return String.format("Action %s succeeded. %s", actionType, result.output());
    }

    private String generateFailureReflection(AgentAction.ActionType actionType,
                                              AgentAction.ActionResult result, AgentState state) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Action %s failed. %s", actionType, result.output()));

        if (consecutiveFailures >= STRATEGIC_REFLECTION_THRESHOLD) {
            sb.append(String.format(" This is failure #%d in a row.", consecutiveFailures + 1));

            // Check if same action type keeps failing
            if (actionType == lastActionType) {
                sb.append(String.format(" The same action (%s) has been failing repeatedly.", actionType));
            }

            // Consult memory for past reflections on this action type
            List<ReasoningTrace> pastFailures = memory.search(actionType.name(), 3);
            if (!pastFailures.isEmpty()) {
                sb.append(" Past reflections suggest: ");
                pastFailures.stream()
                        .map(ReasoningTrace::reflection)
                        .filter(r -> r != null && !r.isEmpty())
                        .distinct()
                        .limit(2)
                        .forEach(r -> sb.append("[").append(r).append("] "));
            }
        }

        return sb.toString();
    }

    private void updateStreaks(boolean success, AgentAction.ActionType actionType) {
        if (success) {
            consecutiveSuccesses++;
            consecutiveFailures = 0;
        } else {
            consecutiveFailures++;
            consecutiveSuccesses = 0;
        }
        lastActionType = actionType;
    }

    /**
     * Immutable result of an evaluation.
     */
    public record EvaluationResult(
            boolean success,
            String reflection,
            int consecutiveFailures,
            int consecutiveSuccesses
    ) {}
}
