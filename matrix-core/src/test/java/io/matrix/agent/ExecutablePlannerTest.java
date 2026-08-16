package io.matrix.agent;

import io.matrix.tools.ToolsResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExecutablePlanner step execution with real ToolsResource.
 */
class ExecutablePlannerTest {

    private ExecutablePlanner planner;
    private ToolsResource tools;

    @BeforeEach
    void setUp() {
        tools = new ToolsResource();
        planner = new ExecutablePlanner();
        planner.tools = tools;
    }

    @Test
    void calculatorReturnsCorrectResult() {
        var steps = List.of(
                PlanStep.invokeTool("calculator",
                        Map.of("expression", "2+2*3"),
                        "calculate 2+2*3"));

        var result = planner.executeSteps("calc test", steps);

        assertTrue(result.allPassed());
        assertEquals(1, result.steps().size());
        assertTrue(result.steps().get(0).output().contains("8"));
    }

    @Test
    void verifyStepMatchesPreviousOutput() {
        var steps = List.of(
                PlanStep.invokeTool("calculator",
                        Map.of("expression", "42"),
                        "calculate 42"),
                PlanStep.verify(".*42.*", "verify output contains 42"));

        var result = planner.executeSteps("verify test", steps);

        assertTrue(result.allPassed());
        assertEquals(2, result.steps().size());
    }

    @Test
    void verifyStepFailsOnMismatch() {
        var steps = List.of(
                PlanStep.invokeTool("calculator",
                        Map.of("expression", "1"),
                        "calculate 1"),
                PlanStep.verify("XYZ999", "pattern won't match"));

        var result = planner.executeSteps("mismatch test", steps);

        assertFalse(result.allPassed());
    }

    @Test
    void multipleStepsExecuteInOrder() {
        var steps = List.of(
                PlanStep.invokeTool("calculator",
                        Map.of("expression", "100"), "step1"),
                PlanStep.verify(".*100.*", "step2: verify 100"),
                PlanStep.invokeTool("calculator",
                        Map.of("expression", "200"), "step3"),
                PlanStep.verify(".*200.*", "step4: verify 200"));

        var result = planner.executeSteps("multi", steps);

        assertTrue(result.allPassed());
        assertEquals(4, result.steps().size());
    }

    @Test
    void emptyStepsReturnsSuccess() {
        var result = planner.executeSteps("empty", List.of());
        assertTrue(result.allPassed());
    }

    @Test
    void unknownActionReturnsFailure() {
        var step = new PlanStep("unknown", "t", Map.of(), null, "desc");
        var result = planner.executeSteps("test", List.of(step));
        assertFalse(result.allPassed());
    }

    @Test
    void planStepInvokeToolFactoryIsCorrect() {
        var step = PlanStep.invokeTool("web_search",
                Map.of("query", "test"), "search test");
        assertEquals("invoke_tool", step.action());
        assertEquals("web_search", step.toolName());
    }

    @Test
    void planStepVerifyFactoryIsCorrect() {
        var step = PlanStep.verify("\\d+", "check digits");
        assertEquals("verify", step.action());
        assertEquals("\\d+", step.expectedOutputPattern());
    }

    @Test
    void planStepWaitMsFactoryIsCorrect() {
        var step = PlanStep.waitMs(500, "half second wait");
        assertEquals("wait", step.action());
        assertEquals(500L, step.args().get("duration_ms"));
    }
}
