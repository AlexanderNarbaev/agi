package io.matrix.agent.react;

import io.matrix.agent.AgentAction;
import io.matrix.agent.AgentState;
import io.matrix.memory.HierarchicalMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelfEvaluatorTest {

    private HierarchicalMemory longTermMemory;
    private ReflexionMemory memory;
    private SelfEvaluator evaluator;

    @BeforeEach
    void setUp() {
        longTermMemory = new HierarchicalMemory(100);
        memory = new ReflexionMemory(longTermMemory, 50);
        evaluator = new SelfEvaluator(memory);
    }

    // ── Success Evaluation ──

    @Test
    void shouldEvaluateSuccessfulAction() {
        AgentState state = createState(AgentAction.ActionType.MOVE);
        AgentAction.ActionResult result = AgentAction.ActionResult.success("moved north", 10);

        SelfEvaluator.EvaluationResult evaluation = evaluator.evaluate(state, result);

        assertThat(evaluation.success()).isTrue();
        assertThat(evaluation.reflection()).contains("MOVE");
        assertThat(evaluation.reflection()).contains("succeeded");
        assertThat(evaluation.consecutiveSuccesses()).isEqualTo(1);
        assertThat(evaluation.consecutiveFailures()).isZero();
    }

    @Test
    void shouldEvaluateFailedAction() {
        AgentState state = createState(AgentAction.ActionType.MINE);
        AgentAction.ActionResult result = AgentAction.ActionResult.failure("no pickaxe", 5);

        SelfEvaluator.EvaluationResult evaluation = evaluator.evaluate(state, result);

        assertThat(evaluation.success()).isFalse();
        assertThat(evaluation.reflection()).contains("MINE");
        assertThat(evaluation.reflection()).contains("failed");
        assertThat(evaluation.consecutiveFailures()).isEqualTo(1);
        assertThat(evaluation.consecutiveSuccesses()).isZero();
    }

    @Test
    void shouldTreatEmptyOutputOnNonWaitAsFailure() {
        AgentState state = createState(AgentAction.ActionType.MOVE);
        AgentAction.ActionResult result = AgentAction.ActionResult.success("", 0);

        SelfEvaluator.EvaluationResult evaluation = evaluator.evaluate(state, result);

        assertThat(evaluation.success()).isFalse();
    }

    @Test
    void shouldAcceptEmptyOutputOnWaitAction() {
        AgentState state = createState(AgentAction.ActionType.WAIT);
        AgentAction.ActionResult result = AgentAction.ActionResult.success("", 0);

        SelfEvaluator.EvaluationResult evaluation = evaluator.evaluate(state, result);

        assertThat(evaluation.success()).isTrue();
    }

    @Test
    void shouldAcceptEmptyOutputOnObserveAction() {
        AgentState state = createState(AgentAction.ActionType.OBSERVE);
        AgentAction.ActionResult result = AgentAction.ActionResult.success("", 0);

        SelfEvaluator.EvaluationResult evaluation = evaluator.evaluate(state, result);

        assertThat(evaluation.success()).isTrue();
    }

    // ── Streak Tracking ──

    @Test
    void shouldTrackConsecutiveSuccesses() {
        AgentState state = createState(AgentAction.ActionType.MOVE);
        AgentAction.ActionResult success = AgentAction.ActionResult.success("ok", 1);

        evaluator.evaluate(state, success);
        evaluator.evaluate(state, success);
        SelfEvaluator.EvaluationResult third = evaluator.evaluate(state, success);

        assertThat(third.consecutiveSuccesses()).isEqualTo(3);
        assertThat(third.consecutiveFailures()).isZero();
    }

    @Test
    void shouldTrackConsecutiveFailures() {
        AgentState state = createState(AgentAction.ActionType.MINE);
        AgentAction.ActionResult failure = AgentAction.ActionResult.failure("error", 1);

        evaluator.evaluate(state, failure);
        evaluator.evaluate(state, failure);
        evaluator.evaluate(state, failure);
        SelfEvaluator.EvaluationResult fourth = evaluator.evaluate(state, failure);

        assertThat(fourth.consecutiveFailures()).isEqualTo(4);
        assertThat(fourth.consecutiveSuccesses()).isZero();
    }

    @Test
    void shouldResetSuccessStreakOnFailure() {
        AgentState state = createState(AgentAction.ActionType.MOVE);
        AgentAction.ActionResult success = AgentAction.ActionResult.success("ok", 1);
        AgentAction.ActionResult failure = AgentAction.ActionResult.failure("error", 1);

        evaluator.evaluate(state, success);
        evaluator.evaluate(state, success);
        SelfEvaluator.EvaluationResult afterFail = evaluator.evaluate(state, failure);

        assertThat(afterFail.consecutiveSuccesses()).isZero();
        assertThat(afterFail.consecutiveFailures()).isEqualTo(1);
    }

    @Test
    void shouldResetFailureStreakOnSuccess() {
        AgentState state = createState(AgentAction.ActionType.MINE);
        AgentAction.ActionResult success = AgentAction.ActionResult.success("ok", 1);
        AgentAction.ActionResult failure = AgentAction.ActionResult.failure("error", 1);

        evaluator.evaluate(state, failure);
        evaluator.evaluate(state, failure);
        SelfEvaluator.EvaluationResult afterSuccess = evaluator.evaluate(state, success);

        assertThat(afterSuccess.consecutiveFailures()).isZero();
        assertThat(afterSuccess.consecutiveSuccesses()).isEqualTo(1);
    }

    @Test
    void shouldResetStreaks() {
        AgentState state = createState(AgentAction.ActionType.MOVE);
        AgentAction.ActionResult success = AgentAction.ActionResult.success("ok", 1);

        evaluator.evaluate(state, success);
        evaluator.evaluate(state, success);
        evaluator.resetStreaks();

        assertThat(evaluator.consecutiveFailures()).isZero();
        assertThat(evaluator.consecutiveSuccesses()).isZero();
    }

    // ── Strategic Reflection ──

    @Test
    void shouldNotGenerateStrategicReflectionBelowThreshold() {
        AgentState state = createState(AgentAction.ActionType.MINE);
        AgentAction.ActionResult failure = AgentAction.ActionResult.failure("error", 1);

        evaluator.evaluate(state, failure);
        evaluator.evaluate(state, failure);

        String strategic = evaluator.generateStrategicReflection();
        assertThat(strategic).isNull();
    }

    @Test
    void shouldGenerateStrategicReflectionAfterThreshold() {
        AgentState state = createState(AgentAction.ActionType.MINE);
        AgentAction.ActionResult failure = AgentAction.ActionResult.failure("error", 1);

        // Need 3 consecutive failures to trigger strategic reflection
        // Also store traces so memory has failure patterns
        for (int i = 0; i < 3; i++) {
            SelfEvaluator.EvaluationResult eval = evaluator.evaluate(state, failure);
            evaluator.buildAndStoreTrace(i + 1, 0L, "thought",
                    new AgentAction(AgentAction.ActionType.MINE), failure, eval);
        }

        String strategic = evaluator.generateStrategicReflection();
        assertThat(strategic).isNotNull();
        assertThat(strategic).contains("3 consecutive failures");
        assertThat(strategic).contains("Recommendation");
    }

    // ── Trace Building ──

    @Test
    void shouldBuildAndStoreTrace() {
        AgentState state = createState(AgentAction.ActionType.CRAFT);
        AgentAction.ActionResult result = AgentAction.ActionResult.success("crafted sword", 20);
        SelfEvaluator.EvaluationResult evaluation = evaluator.evaluate(state, result);

        ReasoningTrace trace = evaluator.buildAndStoreTrace(
                42, 0xABCDL, "need a weapon",
                new AgentAction(AgentAction.ActionType.CRAFT), result, evaluation);

        assertThat(trace.tick()).isEqualTo(42);
        assertThat(trace.observation()).isEqualTo(0xABCDL);
        assertThat(trace.thought()).isEqualTo("need a weapon");
        assertThat(trace.action().type()).isEqualTo(AgentAction.ActionType.CRAFT);
        assertThat(trace.actionResult()).isEqualTo("crafted sword");
        assertThat(trace.actionSuccess()).isTrue();
        assertThat(trace.reasoningChain()).hasSize(5);

        // Verify it was stored in memory
        assertThat(memory.size()).isEqualTo(1);
    }

    // ── Reflection Content ──

    @Test
    void shouldIncludeStreakInfoInFailureReflection() {
        AgentState state = createState(AgentAction.ActionType.MOVE);
        AgentAction.ActionResult failure = AgentAction.ActionResult.failure("blocked", 1);

        evaluator.evaluate(state, failure);
        evaluator.evaluate(state, failure);
        evaluator.evaluate(state, failure);
        SelfEvaluator.EvaluationResult fourth = evaluator.evaluate(state, failure);

        assertThat(fourth.reflection()).contains("failure #4");
    }

    @Test
    void shouldIncludeOutputInReflection() {
        AgentState state = createState(AgentAction.ActionType.EXPLORE);
        AgentAction.ActionResult result = AgentAction.ActionResult.success("found cave entrance", 15);

        SelfEvaluator.EvaluationResult evaluation = evaluator.evaluate(state, result);

        assertThat(evaluation.reflection()).contains("found cave entrance");
    }

    // ── Helpers ──

    private AgentState createState(AgentAction.ActionType actionType) {
        AgentAction action = new AgentAction(actionType);
        AgentAction executedAction = action.withResult(AgentAction.ActionResult.empty());
        return new AgentState(0x1000L, new boolean[]{true, false, false, false, false},
                executedAction, new double[]{0.3, 0.2, 0.5}, 1);
    }
}
