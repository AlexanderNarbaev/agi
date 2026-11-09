package io.matrix.cognitive.reflection;

import io.matrix.cognitive.ErrorSignal;
import io.matrix.cognitive.Prediction;
import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.contract.Reflection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 §4 — the cycle's reflection.
 *
 * <p>The mandate asks for reflection that "must produce machine-readable changes or
 * decisions that can be evaluated". These tests hold the reflection to that: it must name
 * what was attempted, which method was chosen and why, what was predicted, what happened,
 * what was learned, whether the method should be used again, and what is still uncertain —
 * and it must produce decisions that can be carried out.</p>
 */
class CycleReflectionTest {

    private static final String AT = "2026-11-08T00:00:00Z";

    private static AlgorithmDecision decision(String methodId, String reason) {
        return new AlgorithmDecision("micro-world", methodId, "v1", reason, List.of(),
                null, AlgorithmDecision.Status.COMPLETED, null, "the method answered");
    }

    private static Prediction prediction(String value) {
        return new Prediction("prd-ep-1", "ws", "ep-1", "lamp@power=1,switch=1", value,
                "surviving-hypotheses", "v1", 1.0, false, List.of("case-1"), AT);
    }

    private static ErrorSignal error() {
        return new ErrorSignal("err-ep-1", "ws", "prd-ep-1", "true", "false", 1.0,
                ErrorSignal.Disposition.UNRESOLVED, "micro-world-hypothesis-set", "v1",
                List.of("case-1"), AT);
    }

    @Test
    @DisplayName("the reflection names what was attempted, chosen, predicted and observed")
    void theReflectionNamesTheCycle() {
        Reflection reflection = CycleReflection.of("ep-1", "task-ep-1",
                decision("micro-world-reference", "it is the only admitted method"),
                prediction("true"), null, Boolean.TRUE,
                List.of("a case was recorded"), List.of(), AT);

        assertEquals("ref-ep-1", reflection.id());
        assertEquals("ep-1", reflection.episodeId());
        assertEquals("task-ep-1", reflection.taskId());
        assertEquals("micro-world-reference", reflection.methodId());
        assertTrue(reflection.methodSelectionReason().contains("admitted"));
        assertEquals("prd-ep-1", reflection.predictionId());
        assertEquals("true", reflection.predictedOutcome());
        assertEquals("true", reflection.observedOutcome());
        assertTrue(reflection.learnedSomething());
        assertEquals(1, reflection.learned().size());
    }

    @Test
    @DisplayName("a confirmed outcome produces a decision to prefer the method")
    void aConfirmedOutcomePrefersTheMethod() {
        Reflection reflection = CycleReflection.of("ep-1", "task-ep-1",
                decision("micro-world-reference", "the only admitted method"),
                prediction("true"), null, Boolean.TRUE,
                List.of("a case was recorded"), List.of(), AT);

        assertTrue(reflection.reuseMethod(),
                "a method whose answer was confirmed must be reused");
        assertEquals(1, reflection.decisions().size());
        Reflection.Decision decision = reflection.decisions().get(0);
        assertEquals(Reflection.DECISION_PREFER_METHOD, decision.kind());
        assertEquals("micro-world-reference", decision.subjectId());
        assertNotNull(decision.rationale());
        assertTrue(decision.parameters().containsKey("episodeId"));
    }

    @Test
    @DisplayName("a contradicted outcome withdraws the method and says what is uncertain")
    void aContradictedOutcomeWithdrawsTheMethod() {
        Reflection reflection = CycleReflection.of("ep-1", "task-ep-1",
                decision("micro-world-reference", "the only admitted method"),
                prediction("true"), error(), null,
                List.of("the disagreement was recorded"), List.of("the model is in doubt"), AT);

        assertFalse(reflection.reuseMethod(),
                "a method whose prediction was contradicted must be withdrawn");
        assertEquals(Reflection.DECISION_WITHDRAW_METHOD,
                reflection.decisions().get(0).kind());
        assertEquals("the model is in doubt", reflection.uncertainties().get(0));
        assertNotNull(reflection.discrepancy());
        assertTrue(reflection.discrepancy().contains("disagree"));
        assertTrue(reflection.decisions().stream()
                        .anyMatch(d -> Reflection.DECISION_LEAVE_UNRESOLVED.equals(d.kind())),
                "an unresolved proposition must produce a decision that says so");
    }

    @Test
    @DisplayName("a method that could not answer is neither preferred nor withdrawn")
    void aCycleThatDidNotAnswerIsNeutral() {
        Reflection reflection = CycleReflection.of("ep-1", "task-ep-1",
                decision("micro-world-reference", "the only admitted method"),
                prediction("true"), null, null, List.of(), List.of("the model is unsettled"), AT);

        assertTrue(reflection.reuseMethod(),
                "a method that could not answer because the MODEL is unsettled has not failed");
        assertTrue(reflection.decisions().stream()
                        .noneMatch(d -> Reflection.DECISION_PREFER_METHOD.equals(d.kind())),
                "a method that produced no answer must not be preferred");
        assertTrue(reflection.decisions().stream()
                        .noneMatch(d -> Reflection.DECISION_WITHDRAW_METHOD.equals(d.kind())),
                "a method that produced no answer must not be withdrawn");
        assertTrue(reflection.decisions().stream()
                        .anyMatch(d -> Reflection.DECISION_LEAVE_UNRESOLVED.equals(d.kind())),
                "what is unresolved is the proposition, and the cycle must say so");
    }

    @Test
    @DisplayName("the evaluation of a cycle carries the metrics a reader can compare")
    void theEvaluationCarriesMetrics() {
        Reflection reflection = CycleReflection.of("ep-1", "task-ep-1",
                decision("micro-world-reference", "the only admitted method"),
                prediction("true"), null, Boolean.TRUE,
                List.of("a case was recorded"), List.of(), AT);

        io.matrix.contract.Evaluation evaluation =
                CycleReflection.evaluationOf("ep-1", reflection, null, 1, AT);

        assertEquals("eval-ep-1", evaluation.id());
        Map<String, io.matrix.contract.Evaluation.Metric> metrics = evaluation.metrics();
        assertEquals(1.0, metrics.get("predictions.confirmed").value());
        assertEquals(1.0, metrics.get("method.reusable").value());
        assertEquals(1.0, metrics.get("things.learned").value());
        assertEquals(4, metrics.size());
    }
}
