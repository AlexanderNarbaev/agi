package io.matrix.actions;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PlanPreprocessor} (DESIGN-15 §3 fast-fail via AC-3).
 */
class PlanPreprocessorTest {

    @Test
    void satisfiableStepPasses() {
        var step = new PlanPreprocessor.PlanStep("move",
                List.of("x", "y"),
                Map.of("x", 3, "y", 2),
                List.of(new int[]{0, 1}));
        assertThatCode(() -> PlanPreprocessor.preprocess(List.of(step)))
                .doesNotThrowAnyException();
    }

    @Test
    void missingDomainFailsFastAsUnsatisfiable() {
        var step = new PlanPreprocessor.PlanStep("grab",
                List.of("a", "b"),
                Map.of("a", 2), // domain for "b" is missing → empty
                List.of());
        assertThatThrownBy(() -> PlanPreprocessor.preprocess(List.of(step)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsatisfiable_preconditions")
                .hasMessageContaining("grab");
    }

    @Test
    void malformedArcRejected() {
        var step = new PlanPreprocessor.PlanStep("bad",
                List.of("a"), Map.of("a", 2), List.of(new int[]{0, 1, 2}));
        assertThatThrownBy(() -> PlanPreprocessor.preprocess(List.of(step)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pair");
    }

    @Test
    void multipleStepsCheckedInOrder() {
        var ok = new PlanPreprocessor.PlanStep("s1",
                List.of("v"), Map.of("v", 4), List.of());
        var bad = new PlanPreprocessor.PlanStep("s2",
                List.of("u", "w"), Map.of("u", 1), List.of());
        assertThatThrownBy(() -> PlanPreprocessor.preprocess(List.of(ok, bad)))
                .hasMessageContaining("s2");
    }
}
